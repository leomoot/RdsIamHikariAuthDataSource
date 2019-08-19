package nl.leomoot.app.jdbc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.rds.auth.GetIamAuthTokenRequest;
import com.amazonaws.services.rds.auth.RdsIamAuthTokenGenerator;
import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;

import org.apache.commons.lang3.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * DataSource that supports IAM authentication to RDS.
 * 
 * @author <a href="mailto:leo.moot@gmail.com">Leo Moot</a>
 * @author <a href="mailto:leo.moot@luminis.eu">Leo Moot</a>
 */
@Slf4j
public class RdsIamHikariAuthDataSource extends HikariDataSource implements Runnable {

    /**
    * The root certificate working that works for all AWS Regions.
    * Downloaded from https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html
    */
    private static final String SSL_CERTIFICATE = "rds-ca-2015-root.pem";

    private static final String KEY_STORE_TYPE = "JKS";
    private static final String KEY_STORE_PROVIDER = "SUN";
    private static final String KEY_STORE_FILE_PREFIX = "sys-connect-via-ssl-test-cacerts";
    private static final String KEY_STORE_FILE_SUFFIX = ".jks";
    private static final String ROOT_CA_CERTIFICATE = "rootCaCertificate";
    private static final String DEFAULT_KEY_STORE_PASSWORD = "changeit";
    private static final String TOKEN_REFRESH_THREAD_NAME = "RdsIamHikariAuthDataSourceThread";

    private static final Long MS_BEFORE_REFRESHING_TOKEN = Long.valueOf(600000); // 10 minutes

    /**
     * Default RDS port
     */
    private static final int RDS_INSTANCE_PORT = 3306;

    private RdsIamAuthTokenGenerator rdsIamAuthTokenGenerator;
    private String host;
    private String region;
    private int port;
    private Thread tokenThread;

    /**
     * Default constructor, responsible for setting the SSL properties.
     * 
     * @throws Exception
     */
    public RdsIamHikariAuthDataSource() throws Exception {
        super();
        super.setDataSourceProperties(getSSLProperties());
    }

    /** 
     * {@inheritDoc} 
     */
    @Override
    public Connection getConnection() throws SQLException {
        if (super.isRunning()) {
            return super.getConnection();
        }

        URI uri;
        try {
            uri = new URI(super.getJdbcUrl().substring(5));
        } catch (URISyntaxException usEx) {
            throw new RuntimeException(usEx.getMessage());
        }

        host = uri.getHost();
        port = uri.getPort();
        if (port < 0) {
            port = RDS_INSTANCE_PORT;
        }
        
        // extract region from rds hostname
        region = StringUtils.split(host, '.')[2];

        log.info(String.format("Initializing connection pool using host %s:%s in region %s...", host, port,
                region));
        
        // initialize the token generator with the above region
        rdsIamAuthTokenGenerator = RdsIamAuthTokenGenerator.builder().credentials(
            new DefaultAWSCredentialsProviderChain()).region(region).build();

        // set initial first time password / token
        setPassword(generateToken());

        // ... and use them here
        Connection connection = super.getConnection();

        // create & start the token refresh thread
        createTokenRefreshThread();
   
        return connection;
    }
    
    /**
     * Creates the token refresh thread.
     */
    private void createTokenRefreshThread() {
        tokenThread = new Thread(this, TOKEN_REFRESH_THREAD_NAME);
        tokenThread.setDaemon(true);
        tokenThread.start();
    }

    /**
     * Returns the SSL properties which specify the key store file, its type and password.
     * 
     * @throws Exception
     */
    private static Properties getSSLProperties() throws Exception {
        Properties props = new Properties();
        props.setProperty("useSSL", "true");
        props.setProperty("requireSSL", "true");
        props.setProperty("verifyServerCertificate", "true");
        props.setProperty("trustCertificateKeyStoreUrl", createKeyStoreFile());
        props.setProperty("trustCertificateKeyStorePassword", DEFAULT_KEY_STORE_PASSWORD);

        return props; 
    }

    /**
     * This method returns the path of the Key Store File needed for the SSL verification during the IAM Database Authentication to
     * the db instance.
     * 
     * @return the path of a KeyStore File.
     * @throws Exception
     */
    private static String createKeyStoreFile() throws Exception {
        return createKeyStoreFile(createCertificate());
    }

    /**
     * Generates the SSL certificate
     * 
     * @return a SSL certificate 
     * @throws Exception
     */
    private static X509Certificate createCertificate() throws Exception {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

        try (InputStream certInputStream = RdsIamHikariAuthDataSource.class.getClassLoader().getResourceAsStream(SSL_CERTIFICATE)) {
            return (X509Certificate) certFactory.generateCertificate(certInputStream);
        }
    }

    /**
     * Creates the KeyStore File.
     * 
     * @param rootX509Certificate - the SSL certificate to be stored in the KeyStore.
     * @return path to a KeyStore
     * @throws KeyStoreException
     * @throws Exception
     */
    private static String createKeyStoreFile(X509Certificate rootX509Certificate) throws Exception {
        File keyStoreFile = File.createTempFile(KEY_STORE_FILE_PREFIX, KEY_STORE_FILE_SUFFIX);
        try (FileOutputStream fos = new FileOutputStream(keyStoreFile.getPath())) {
            KeyStore ks = KeyStore.getInstance(KEY_STORE_TYPE, KEY_STORE_PROVIDER);
            ks.load(null);
            ks.setCertificateEntry(ROOT_CA_CERTIFICATE, rootX509Certificate);
            ks.store(fos, DEFAULT_KEY_STORE_PASSWORD.toCharArray());
        }

        return keyStoreFile.toURI().toString();
    }

    /**
     * Refresh thread main loop
     */
    @Override
    public void run() {
        try {
            while (tokenThread != null) {
                Thread.sleep(MS_BEFORE_REFRESHING_TOKEN); // wait for 10 minutes, then recreate the token
                HikariConfigMXBean hikariConfigMXBean = super.getHikariConfigMXBean();
                log.info(String.format("Start refreshing tokens in %s...", hikariConfigMXBean.getPoolName()));
                hikariConfigMXBean.setPassword(generateToken());
            }
        } catch (InterruptedException irqEx) {
            log.warn("Interrupted", irqEx);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * {@inheritDoc} 
     */
    @Override
    public void close() {
        super.close();
        Thread copy = tokenThread;
        tokenThread = null;
        if (copy != null) {
            copy.interrupt();
        }
    }

    /**
     * Generates a new token.
     * @return the newly generated token.
     */
    private String generateToken() {
        String token = rdsIamAuthTokenGenerator.getAuthToken(
                GetIamAuthTokenRequest.builder().hostname(host).port(port).userName(super.getUsername()).build());
        log.debug(String.format("Generated authentication token: %s", token));

        return token;
    }
}
# RdsIamHikariAuthDataSource

Accessing AWS RDS using IAM Authentication and HikariCP / Spring Boot 2.1.x 
The RDS instance should have IAM Authentication enabled. You need to create a user with the following
policy attached:
```
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "rds-db:connect"
            ],
            "Resource": [
                "arn:aws:rds-db:eu-central-1:231975473731:dbuser:db-QLX3INGBVG3FEW5NRMGS2Q5VKE/rds-iam-user"
            ]
        }
    ]
}
```

Permission to modify and redistribute is granted under the terms of the 
[Apache License 2.0] [license-url]


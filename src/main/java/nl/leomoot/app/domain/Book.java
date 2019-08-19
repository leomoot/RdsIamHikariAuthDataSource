package nl.leomoot.app.domain;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Books
 */
@NoArgsConstructor
@Getter
@Entity
@Table(name = "books")
public class Book {


    @Id
    private UUID id;
    private String name;
    
    public Book(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
    }
}
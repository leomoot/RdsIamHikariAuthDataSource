package nl.leomoot.app.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import nl.leomoot.app.domain.Book;

/**
 * BookRepository
 */
public interface BookRepository extends JpaRepository<Book, UUID> {
}
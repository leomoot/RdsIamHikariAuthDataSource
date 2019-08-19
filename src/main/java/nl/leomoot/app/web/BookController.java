package nl.leomoot.app.web;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.Getter;
import lombok.Setter;
import nl.leomoot.app.domain.Book;
import nl.leomoot.app.repository.BookRepository;

import java.util.Collection;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


/**
 * BookController
 */
@RestController
@RequestMapping("/books")
public class BookController {
    private BookRepository bookRepository;

    public BookController(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }
    
    @GetMapping
    public Collection<BookView> get() {
        return bookRepository.findAll().stream().map(BookView::new).collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<?> post(@RequestBody CreateBookRequest request) {
        bookRepository.save(new Book(request.getName()));
        return ResponseEntity.ok().build();
    }
    
    @Getter
    @Setter
    static class CreateBookRequest {
        private String name;
        private String name2;
    }

    @Getter
   static class BookView {
        private String id;
        private String name;
    
        public BookView(Book book) {
            this.id = book. getId().toString();
            this.name = book.getName();
        }   
    }



}
package io.bookmind.server.api;

import io.bookmind.server.library.BookDetail;
import io.bookmind.server.library.BookSummary;
import io.bookmind.server.library.ChapterSummary;
import io.bookmind.server.library.LibraryService;
import io.bookmind.spi.BookCover;
import io.bookmind.spi.Chapter;
import io.bookmind.spi.PluginException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final LibraryService libraryService;

    public BookController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @GetMapping
    public List<BookSummary> listBooks(
            @RequestParam(name = "format", required = false) String format,
            @RequestParam(name = "q", required = false) String query
    ) {
        return libraryService.listBooks(Optional.ofNullable(format), Optional.ofNullable(query));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BookDetail> uploadBook(@RequestParam("file") MultipartFile file)
            throws IOException, PluginException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }
        BookDetail created = libraryService.uploadBook(file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public BookDetail getBook(@PathVariable("id") String bookId) {
        return libraryService.getBook(bookId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable("id") String bookId) {
        libraryService.deleteBook(bookId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/chapters")
    public List<ChapterSummary> getChapters(@PathVariable("id") String bookId) {
        return libraryService.getChapters(bookId);
    }

    @GetMapping("/{id}/chapters/{idx}")
    public Chapter getChapter(@PathVariable("id") String bookId, @PathVariable("idx") int chapterIndex) {
        return libraryService.getChapter(bookId, chapterIndex);
    }

    @GetMapping("/{id}/cover")
    public ResponseEntity<byte[]> getCover(@PathVariable("id") String bookId) {
        BookCover cover = libraryService.getCover(bookId);
        MediaType mediaType = MediaType.parseMediaType(cover.mimeType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(cover.bytes());
    }
}

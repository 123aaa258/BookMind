package io.bookmind.server.api;

import io.bookmind.server.library.LibraryService;
import io.bookmind.server.library.LibraryStatus;
import io.bookmind.server.library.ScanResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/library")
public class LibraryController {

    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @GetMapping("/status")
    public LibraryStatus status() {
        return libraryService.getStatus();
    }

    @PostMapping("/scan")
    public ScanResult scan() {
        return libraryService.scanLibrary();
    }
}

package io.bookmind.server.library;

import java.time.Instant;
import java.util.List;

/**
 * Root JSON document persisted to {@code data/library-index.json}.
 */
record LibraryIndexSnapshot(
        Instant lastScanAt,
        List<StoredBookRecord> books,
        List<ScanFailure> failures
) {
    LibraryIndexSnapshot {
        books = books == null ? List.of() : List.copyOf(books);
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    static LibraryIndexSnapshot empty() {
        return new LibraryIndexSnapshot(null, List.of(), List.of());
    }
}

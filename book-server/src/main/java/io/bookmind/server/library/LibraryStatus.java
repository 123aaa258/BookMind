package io.bookmind.server.library;

import java.time.Instant;
import java.util.List;

/**
 * Current library overview returned by {@code GET /api/library/status}.
 */
public record LibraryStatus(
        int bookCount,
        Instant lastScanAt,
        List<ScanFailure> failures
) {
    public LibraryStatus {
        failures = failures == null ? List.of() : List.copyOf(failures);
    }
}

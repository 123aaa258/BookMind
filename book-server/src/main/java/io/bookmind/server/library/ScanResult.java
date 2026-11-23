package io.bookmind.server.library;

import java.time.Instant;
import java.util.List;

/**
 * Summary returned by {@code POST /api/library/scan}.
 */
public record ScanResult(
        int scannedCount,
        int indexedCount,
        int removedCount,
        List<ScanFailure> failures,
        Instant scannedAt
) {
    public ScanResult {
        failures = failures == null ? List.of() : List.copyOf(failures);
    }
}

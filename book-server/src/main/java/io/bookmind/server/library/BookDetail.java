package io.bookmind.server.library;

import java.time.Instant;
import java.util.Map;

/**
 * Detailed book metadata returned by {@code GET /api/books/{id}} and {@code POST /api/books/upload}.
 */
public record BookDetail(
        String id,
        String title,
        String author,
        String language,
        String format,
        int chapterCount,
        boolean hasCover,
        Map<String, String> extraMetadata,
        String fileName,
        Instant importedAt
) {
    public BookDetail {
        extraMetadata = extraMetadata == null ? Map.of() : Map.copyOf(extraMetadata);
    }
}

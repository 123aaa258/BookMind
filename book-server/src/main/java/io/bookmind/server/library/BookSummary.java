package io.bookmind.server.library;

/**
 * Lightweight book card projection returned by {@code GET /api/books}.
 */
public record BookSummary(
        String id,
        String title,
        String author,
        String language,
        String format,
        int chapterCount,
        boolean hasCover
) {
}

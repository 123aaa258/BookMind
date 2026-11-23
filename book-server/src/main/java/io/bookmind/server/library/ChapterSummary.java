package io.bookmind.server.library;

/**
 * Chapter table-of-contents entry returned by {@code GET /api/books/{id}/chapters}.
 */
public record ChapterSummary(int index, String title, String locator) {
}

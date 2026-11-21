package io.bookmind.spi;

import java.util.Objects;

/**
 * Unified logical section of a parsed book (a chapter in TXT or a page/outline section in PDF).
 *
 * @param index   zero-based monotonic order within the book
 * @param title   chapter or page heading displayed in TOC and RAG citations
 * @param locator stable location tag for reader jump-to-source (e.g. {@code "chapter:1"} or {@code "page:12"})
 * @param content plain UTF-8 text content of this section
 */
public record Chapter(
        int index,
        String title,
        String locator,
        String content
) {
    public Chapter {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(locator, "locator must not be null");
        Objects.requireNonNull(content, "content must not be null");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (locator.isBlank()) {
            throw new IllegalArgumentException("locator must not be blank");
        }
    }
}

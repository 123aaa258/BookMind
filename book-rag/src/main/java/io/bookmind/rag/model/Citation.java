package io.bookmind.rag.model;

import java.util.Objects;

/**
 * Numbered source citation (e.g. [1]) linking an LLM answer back to exact book evidence.
 */
public record Citation(
        int refNumber,
        String bookId,
        String bookTitle,
        int chapterIndex,
        String chapterTitle,
        String locator,
        String excerpt,
        float score
) {
    public Citation {
        if (refNumber <= 0) {
            throw new IllegalArgumentException("refNumber must be >= 1");
        }
        Objects.requireNonNull(bookId, "bookId must not be null");
        Objects.requireNonNull(bookTitle, "bookTitle must not be null");
        Objects.requireNonNull(chapterTitle, "chapterTitle must not be null");
        Objects.requireNonNull(locator, "locator must not be null");
        Objects.requireNonNull(excerpt, "excerpt must not be null");
    }
}

package io.bookmind.rag.model;

import java.util.Objects;

/**
 * A ranked evidence snippet recalled from Apache Lucene BM25 search.
 */
public record SearchHit(
        String chunkId,
        String bookId,
        String bookTitle,
        int chapterIndex,
        String chapterTitle,
        String locator,
        String snippet,
        float score
) {
    public SearchHit {
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        Objects.requireNonNull(bookId, "bookId must not be null");
        Objects.requireNonNull(bookTitle, "bookTitle must not be null");
        Objects.requireNonNull(chapterTitle, "chapterTitle must not be null");
        Objects.requireNonNull(locator, "locator must not be null");
        Objects.requireNonNull(snippet, "snippet must not be null");
    }
}

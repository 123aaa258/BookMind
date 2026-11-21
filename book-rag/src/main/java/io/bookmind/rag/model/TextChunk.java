package io.bookmind.rag.model;

import java.util.Objects;

/**
 * A sliced text unit derived from a book chapter/page for Lucene indexing.
 *
 * @param chunkId      unique identifier (e.g. {@code bookId + ":" + chapterIndex + ":" + chunkOrder})
 * @param bookId       stable library book ID
 * @param bookTitle    display title of the book
 * @param chapterIndex zero-based chapter/page index
 * @param chapterTitle heading of the chapter/page
 * @param locator      jump-to-source locator (e.g. {@code "chapter:2"} or {@code "page:15"})
 * @param text         chunk body text
 */
public record TextChunk(
        String chunkId,
        String bookId,
        String bookTitle,
        int chapterIndex,
        String chapterTitle,
        String locator,
        String text
) {
    public TextChunk {
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        Objects.requireNonNull(bookId, "bookId must not be null");
        Objects.requireNonNull(bookTitle, "bookTitle must not be null");
        Objects.requireNonNull(chapterTitle, "chapterTitle must not be null");
        Objects.requireNonNull(locator, "locator must not be null");
        Objects.requireNonNull(text, "text must not be null");
    }
}

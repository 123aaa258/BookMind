package io.bookmind.rag;

import io.bookmind.rag.model.TextChunk;
import io.bookmind.spi.Chapter;
import io.bookmind.spi.ParsedBook;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Splits parsed chapters into overlapping character windows for Lucene indexing.
 *
 * <p>Fixed-size windows keep every chunk traceable to its original chapter via
 * {@code bookId}, {@code chapterIndex} and {@code locator}. The overlap prevents a
 * definition from being cut exactly at a chunk boundary.
 */
public class ChapterChunker {

    public static final int DEFAULT_CHUNK_SIZE = 500;
    public static final int DEFAULT_OVERLAP = 80;

    private final int chunkSize;
    private final int overlap;

    public ChapterChunker() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * @param chunkSize maximum characters per chunk, must be greater than zero
     * @param overlap   characters shared by two adjacent chunks, must be in {@code [0, chunkSize)}
     */
    public ChapterChunker(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be > 0");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be in [0, chunkSize)");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public int chunkSize() {
        return chunkSize;
    }

    public int overlap() {
        return overlap;
    }

    /**
     * Slices every non-empty chapter of {@code book} into ordered chunks.
     *
     * @param bookId stable library book identifier
     * @param book   parsed book produced by a {@code BookFormatPlugin}
     * @return immutable list of chunks ordered by chapter index and chunk order
     */
    public List<TextChunk> chunk(String bookId, ParsedBook book) {
        Objects.requireNonNull(bookId, "bookId must not be null");
        Objects.requireNonNull(book, "book must not be null");

        List<TextChunk> chunks = new ArrayList<>();
        for (Chapter chapter : book.chapters()) {
            chunks.addAll(chunkChapter(bookId, book.title(), chapter));
        }
        return List.copyOf(chunks);
    }

    /**
     * Slices a single chapter, skipping whitespace-only windows.
     *
     * @param bookId    stable library book identifier
     * @param bookTitle display title of the book
     * @param chapter   source chapter or PDF page
     * @return ordered chunks preserving chapter metadata
     */
    public List<TextChunk> chunkChapter(String bookId, String bookTitle, Chapter chapter) {
        Objects.requireNonNull(bookId, "bookId must not be null");
        Objects.requireNonNull(bookTitle, "bookTitle must not be null");
        Objects.requireNonNull(chapter, "chapter must not be null");

        String text = chapter.content();
        if (text.isBlank()) {
            return List.of();
        }

        List<TextChunk> chunks = new ArrayList<>();
        int step = chunkSize - overlap;
        int start = 0;
        int order = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String slice = text.substring(start, end);
            if (!slice.isBlank()) {
                chunks.add(new TextChunk(
                        bookId + ":" + chapter.index() + ":" + order,
                        bookId,
                        bookTitle,
                        chapter.index(),
                        chapter.title(),
                        chapter.locator(),
                        slice
                ));
                order++;
            }
            if (end == text.length()) {
                break;
            }
            start += step;
        }
        return List.copyOf(chunks);
    }
}

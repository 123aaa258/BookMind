package io.bookmind.rag;

import io.bookmind.rag.model.RagAnswer;
import io.bookmind.rag.model.SearchHit;
import io.bookmind.spi.ParsedBook;

import java.util.List;
import java.util.Optional;

/**
 * Contract exposed by {@code book-rag} to {@code book-server}.
 *
 * <p>Owned by Role 2 (RAG). Retrieval uses local Apache Lucene BM25 indexing;
 * answer generation calls a configured OpenAI-compatible Chat Completions endpoint
 * (no fake offline generation mode).
 */
public interface RagService {

    /**
     * Slices and indexes (or re-indexes) all chapters of a parsed book.
     *
     * @param bookId stable library book identifier
     * @param book   parsed book produced by built-in or external {@code BookFormatPlugin}
     */
    void indexBook(String bookId, ParsedBook book);

    /**
     * Removes all indexed chunks belonging to {@code bookId}.
     *
     * @param bookId library book identifier
     */
    void removeBook(String bookId);

    /**
     * Performs deterministic local full-text search across all indexed books (or a single book).
     *
     * @param query          user search query
     * @param optionalBookId optional book scope filter
     * @param topK           maximum number of hits
     * @return ranked list of search hits with chapter locators
     */
    List<SearchHit> search(String query, Optional<String> optionalBookId, int topK);

    /**
     * Executes full RAG: retrieves grounded passages from Lucene, then calls the configured
     * OpenAI-compatible endpoint to synthesize a cited answer.
     *
     * @param question       user reading question
     * @param optionalBookId optional book scope filter
     * @param topK           maximum evidence chunks to include in prompt
     * @return structured answer separating generated text from grounded citations
     * @throws RagException with {@link RagErrorCode#LLM_NOT_CONFIGURED} or upstream error codes
     */
    RagAnswer ask(String question, Optional<String> optionalBookId, int topK);
}

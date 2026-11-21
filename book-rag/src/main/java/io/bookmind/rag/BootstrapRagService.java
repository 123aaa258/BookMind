package io.bookmind.rag;

import io.bookmind.rag.model.RagAnswer;
import io.bookmind.rag.model.RagAskRequest;
import io.bookmind.rag.model.SearchHit;
import io.bookmind.spi.ParsedBook;

import java.util.List;
import java.util.Optional;

/**
 * Bootstrap placeholder implementation of {@link RagService}.
 *
 * <p>Role 2 replaces this with the real Lucene BM25 + OpenAI-compatible (JSON &amp; SSE streaming)
 * implementation in PR-R1..PR-R4.
 */
public class BootstrapRagService implements RagService {

    @Override
    public void indexBook(String bookId, ParsedBook book) {
        // Implemented by Role 2 in PR-R1 / PR-R2
    }

    @Override
    public void removeBook(String bookId) {
        // Implemented by Role 2 in PR-R2
    }

    @Override
    public List<SearchHit> search(String query, Optional<String> optionalBookId, int topK) {
        return List.of();
    }

    @Override
    public RagAnswer ask(RagAskRequest request) {
        throw new RagException(
                RagErrorCode.LLM_NOT_CONFIGURED,
                "RAG engine is not yet configured. Role 2 implements Lucene retrieval and OpenAI-compatible client."
        );
    }

    @Override
    public void askStream(RagAskRequest request, RagStreamListener listener) {
        throw new RagException(
                RagErrorCode.LLM_NOT_CONFIGURED,
                "RAG streaming engine is not yet configured. Set BOOKMIND_LLM_API_KEY after Role 2 PR-R3."
        );
    }
}

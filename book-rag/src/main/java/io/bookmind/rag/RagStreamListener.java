package io.bookmind.rag;

import io.bookmind.rag.model.Citation;

import java.util.List;

/**
 * Transport-agnostic callback listener for Streamable HTTP (SSE over HTTP/1.1 chunked) RAG answers.
 *
 * <p>Keeps {@code book-rag} completely decoupled from Spring Web {@code SseEmitter}.
 * Event order: {@link #onCitations(List)} first -> zero or more {@link #onDelta(String)}
 * -> {@link #onComplete(String)} (or {@link #onError(RagException)} on failure).
 */
public interface RagStreamListener {

    void onCitations(List<Citation> citations);

    void onDelta(String textDelta);

    void onComplete(String model);

    void onError(RagException error);
}

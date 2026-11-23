package io.bookmind.server.api;

import io.bookmind.rag.model.ChatMessage;

import java.util.List;

/**
 * HTTP request JSON payload for {@code POST /api/rag/ask} and {@code POST /api/rag/ask/stream}.
 */
public record RagAskHttpRequest(
        String question,
        String bookId,
        Integer topK,
        List<ChatMessage> history
) {
}

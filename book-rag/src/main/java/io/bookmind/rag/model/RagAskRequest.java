package io.bookmind.rag.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Input payload for {@link io.bookmind.rag.RagService#ask(RagAskRequest)} and streaming companion.
 */
public record RagAskRequest(
        String question,
        Optional<String> bookId,
        int topK,
        List<ChatMessage> history
) {
    public RagAskRequest {
        Objects.requireNonNull(question, "question must not be null");
        if (question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        bookId = bookId == null ? Optional.empty() : bookId;
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be > 0");
        }
        history = history == null ? List.of() : List.copyOf(history);
    }

    public static RagAskRequest simple(String question, Optional<String> bookId, int topK) {
        return new RagAskRequest(question, bookId, topK, List.of());
    }
}

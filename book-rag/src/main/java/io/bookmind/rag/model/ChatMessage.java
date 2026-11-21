package io.bookmind.rag.model;

import java.util.Objects;

/**
 * A single conversational turn passed as optional multi-turn history to RAG companion.
 *
 * @param role    OpenAI-compatible message role ({@code "user"} or {@code "assistant"})
 * @param content message body text
 */
public record ChatMessage(String role, String content) {

    public ChatMessage {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(content, "content must not be null");
        if (!"user".equals(role) && !"assistant".equals(role)) {
            throw new IllegalArgumentException("role must be 'user' or 'assistant': " + role);
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
    }
}

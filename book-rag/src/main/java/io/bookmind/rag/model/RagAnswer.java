package io.bookmind.rag.model;

import java.util.List;
import java.util.Objects;

/**
 * Structured response of the RAG reading companion.
 *
 * <p>Strictly separates generated {@code answer} text (from an OpenAI-compatible LLM)
 * from grounded {@code citations} retrieved from local books.
 */
public record RagAnswer(
        String question,
        String answer,
        String model,
        List<Citation> citations
) {
    public RagAnswer {
        Objects.requireNonNull(question, "question must not be null");
        Objects.requireNonNull(answer, "answer must not be null");
        Objects.requireNonNull(model, "model must not be null");
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}

package io.bookmind.rag;

import java.util.Objects;

/**
 * Exception raised by {@link RagService} when retrieval or OpenAI-compatible LLM generation fails.
 */
public class RagException extends RuntimeException {

    private final RagErrorCode errorCode;

    public RagException(RagErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public RagException(RagErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public RagErrorCode errorCode() {
        return errorCode;
    }
}

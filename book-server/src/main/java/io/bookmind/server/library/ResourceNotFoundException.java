package io.bookmind.server.library;

import java.util.Objects;

/**
 * Domain exception raised when a requested book, chapter, or cover does not exist.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String errorCode;

    public ResourceNotFoundException(String errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public String errorCode() {
        return errorCode;
    }
}

package io.bookmind.server.api;

import java.time.Instant;
import java.util.Map;

/**
 * Unified JSON error envelope shared across all BookMind REST endpoints.
 */
public record ApiErrorResponse(
        String code,
        String message,
        Map<String, Object> details,
        Instant timestamp
) {
    public static ApiErrorResponse of(String code, String message, Map<String, Object> details) {
        return new ApiErrorResponse(
                code,
                message,
                details == null ? Map.of() : Map.copyOf(details),
                Instant.now()
        );
    }
}

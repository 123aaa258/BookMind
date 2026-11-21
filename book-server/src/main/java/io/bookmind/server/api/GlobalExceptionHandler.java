package io.bookmind.server.api;

import io.bookmind.rag.RagErrorCode;
import io.bookmind.rag.RagException;
import io.bookmind.spi.PluginException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Maps contract exceptions ({@link PluginException}, {@link RagException}, validation errors)
 * to consistent HTTP statuses and {@link ApiErrorResponse} JSON payloads.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PluginException.class)
    public ResponseEntity<ApiErrorResponse> handlePluginException(PluginException ex) {
        HttpStatus status = switch (ex.errorCode()) {
            case UNSUPPORTED_FORMAT, CORRUPTED_FILE, ENCRYPTED_DOCUMENT, EMPTY_CONTENT ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case PLUGIN_DISABLED, INCOMPATIBLE_API_VERSION -> HttpStatus.SERVICE_UNAVAILABLE;
            case PLUGIN_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case IO_ERROR, PLUGIN_EXECUTION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(ex.errorCode().name(), ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(RagException.class)
    public ResponseEntity<ApiErrorResponse> handleRagException(RagException ex) {
        HttpStatus status = switch (ex.errorCode()) {
            case INVALID_QUESTION -> HttpStatus.BAD_REQUEST;
            case NO_EVIDENCE_FOUND -> HttpStatus.NOT_FOUND;
            case LLM_NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
            case LLM_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case LLM_UPSTREAM_ERROR, LLM_MALFORMED_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case INDEX_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        Map<String, Object> details = ex.errorCode() == RagErrorCode.LLM_NOT_CONFIGURED
                ? Map.of("requiredEnvVar", "BOOKMIND_LLM_API_KEY")
                : Map.of();
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(ex.errorCode().name(), ex.getMessage(), details));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("BAD_REQUEST", ex.getMessage(), Map.of()));
    }
}

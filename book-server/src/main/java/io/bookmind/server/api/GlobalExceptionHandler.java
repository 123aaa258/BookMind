package io.bookmind.server.api;

import io.bookmind.rag.RagErrorCode;
import io.bookmind.rag.RagException;
import io.bookmind.server.library.ResourceNotFoundException;
import io.bookmind.spi.PluginException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Map;

/**
 * Maps contract and domain exceptions to consistent HTTP statuses and JSON
 * {@link ApiErrorResponse} payloads (even when raised from SSE or binary endpoints).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return jsonError(HttpStatus.NOT_FOUND, ex.errorCode(), ex.getMessage(), Map.of());
    }

    @ExceptionHandler(PluginException.class)
    public ResponseEntity<ApiErrorResponse> handlePluginException(PluginException ex) {
        HttpStatus status = switch (ex.errorCode()) {
            case UNSUPPORTED_FORMAT, CORRUPTED_FILE, ENCRYPTED_DOCUMENT, EMPTY_CONTENT ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case PLUGIN_DISABLED, INCOMPATIBLE_API_VERSION -> HttpStatus.SERVICE_UNAVAILABLE;
            case PLUGIN_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case IO_ERROR, PLUGIN_EXECUTION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return jsonError(status, ex.errorCode().name(), ex.getMessage(), Map.of());
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
        return jsonError(status, ex.errorCode().name(), ex.getMessage(), details);
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex) {
        return jsonError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handlePayloadTooLarge(MaxUploadSizeExceededException ex) {
        return jsonError(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "FILE_TOO_LARGE",
                "Uploaded file exceeds maximum allowed size (50MB)",
                Map.of()
        );
    }

    private static ResponseEntity<ApiErrorResponse> jsonError(
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> details
    ) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(code, message, details));
    }
}

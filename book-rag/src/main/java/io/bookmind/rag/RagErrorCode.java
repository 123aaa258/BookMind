package io.bookmind.rag;

/**
 * Standardized RAG error codes for retrieval and OpenAI-compatible LLM calls.
 */
public enum RagErrorCode {
    INVALID_QUESTION,
    INDEX_ERROR,
    NO_EVIDENCE_FOUND,
    LLM_NOT_CONFIGURED,
    LLM_TIMEOUT,
    LLM_UPSTREAM_ERROR,
    LLM_MALFORMED_RESPONSE
}

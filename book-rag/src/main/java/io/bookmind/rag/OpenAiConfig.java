package io.bookmind.rag;

import java.time.Duration;

/**
 * Configuration of an OpenAI-compatible Chat Completions endpoint.
 *
 * <p>All values are injected from environment variables; API keys are never persisted by
 * {@code book-rag}. An empty API key marks the engine as not configured.
 *
 * @param baseUrl chat completions base URL, e.g. {@code https://api.openai.com/v1}
 * @param apiKey  bearer token; blank means not configured
 * @param model   model name sent in the request payload
 * @param timeout request timeout
 */
public record OpenAiConfig(String baseUrl, String apiKey, String model, Duration timeout) {

    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_MODEL = "gpt-4o-mini";
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    public OpenAiConfig {
        baseUrl = normalizeBaseUrl(baseUrl);
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        timeout = timeout == null || timeout.isZero() || timeout.isNegative() ? DEFAULT_TIMEOUT : timeout;
    }

    /**
     * @return {@code true} when a non-blank API key was provided
     */
    public boolean configured() {
        return !apiKey.isBlank();
    }

    /**
     * Builds a configuration from {@code BOOKMIND_LLM_*} environment variables.
     *
     * @return configuration with defaults for missing values
     */
    public static OpenAiConfig fromEnvironment() {
        return new OpenAiConfig(
                env("BOOKMIND_LLM_BASE_URL", DEFAULT_BASE_URL),
                env("BOOKMIND_LLM_API_KEY", ""),
                env("BOOKMIND_LLM_MODEL", DEFAULT_MODEL),
                Duration.ofSeconds(parseSeconds(env("BOOKMIND_LLM_TIMEOUT_SECONDS", "30")))
        );
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static long parseSeconds(String rawValue) {
        try {
            long seconds = Long.parseLong(rawValue);
            return seconds > 0 ? seconds : DEFAULT_TIMEOUT.toSeconds();
        } catch (NumberFormatException e) {
            return DEFAULT_TIMEOUT.toSeconds();
        }
    }
}

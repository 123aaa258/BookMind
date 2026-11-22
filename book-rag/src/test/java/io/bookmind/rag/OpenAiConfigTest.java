package io.bookmind.rag;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiConfigTest {

    @Test
    void blankApiKeyMeansNotConfiguredAndDefaultsAreApplied() {
        OpenAiConfig config = new OpenAiConfig("  https://example.com/v1/  ", "   ", null, null);

        assertThat(config.baseUrl()).isEqualTo("https://example.com/v1");
        assertThat(config.configured()).isFalse();
        assertThat(config.model()).isEqualTo(OpenAiConfig.DEFAULT_MODEL);
        assertThat(config.timeout()).isEqualTo(OpenAiConfig.DEFAULT_TIMEOUT);
    }

    @Test
    void explicitValuesAreTrimmedAndKept() {
        OpenAiConfig config = new OpenAiConfig(
                "http://localhost:1234/v1", "  secret-key  ", "qwen-plus", Duration.ofSeconds(5));

        assertThat(config.baseUrl()).isEqualTo("http://localhost:1234/v1");
        assertThat(config.apiKey()).isEqualTo("secret-key");
        assertThat(config.configured()).isTrue();
        assertThat(config.model()).isEqualTo("qwen-plus");
        assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(5));
    }
}

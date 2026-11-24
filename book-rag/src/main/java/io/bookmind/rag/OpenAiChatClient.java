package io.bookmind.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Minimal OpenAI-compatible Chat Completions client built on the JDK 21 {@link HttpClient}.
 *
 * <p>Supports both synchronous JSON responses and Streamable HTTP SSE streaming. It deliberately
 * contains no Spring dependency so that {@code book-rag} stays transport-agnostic.
 */
public class OpenAiChatClient {

    private static final int ERROR_BODY_MAX_CHARS = 300;
    private static final double TEMPERATURE = 0.2;

    private final OpenAiConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiChatClient(OpenAiConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * @return {@code true} when an API key is available
     */
    public boolean configured() {
        return config.configured();
    }

    /**
     * @return configured model name
     */
    public String model() {
        return config.model();
    }

    /**
     * Executes a non-streaming Chat Completions call.
     *
     * @param messages ordered system/history/user prompt messages
     * @return generated content and the model reported by the endpoint
     * @throws RagException with a standardized {@link RagErrorCode}
     */
    public ChatCompletion complete(List<ChatPrompt> messages) {
        ensureConfigured();
        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    request(payload(messages, false)),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (HttpTimeoutException e) {
            throw timeout(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RagException(RagErrorCode.LLM_TIMEOUT, "LLM request was interrupted", e);
        } catch (IOException e) {
            throw new RagException(RagErrorCode.LLM_UPSTREAM_ERROR,
                    "Failed to call LLM endpoint: " + e.getMessage(), e);
        }

        ensureSuccess(response.statusCode(), response.body());
        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (!contentNode.isTextual() || contentNode.asText().isBlank()) {
                throw new RagException(RagErrorCode.LLM_MALFORMED_RESPONSE,
                        "LLM response is missing choices[0].message.content");
            }
            String model = root.path("model").asText(config.model());
            return new ChatCompletion(contentNode.asText(), model);
        } catch (JsonProcessingException e) {
            throw new RagException(RagErrorCode.LLM_MALFORMED_RESPONSE, "LLM returned malformed JSON", e);
        }
    }

    /**
     * Executes a streaming Chat Completions call ({@code "stream": true}) and pushes token deltas.
     *
     * @param messages ordered prompt messages
     * @param onDelta  invoked for every non-empty content delta
     * @param onModel  invoked once after the stream ends with the reported model name
     * @throws RagException with a standardized {@link RagErrorCode}
     */
    public void stream(List<ChatPrompt> messages, Consumer<String> onDelta, Consumer<String> onModel) {
        ensureConfigured();
        HttpResponse<Stream<String>> response;
        try {
            response = httpClient.send(
                    request(payload(messages, true)),
                    HttpResponse.BodyHandlers.ofLines()
            );
        } catch (HttpTimeoutException e) {
            throw timeout(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RagException(RagErrorCode.LLM_TIMEOUT, "LLM stream request was interrupted", e);
        } catch (IOException e) {
            throw new RagException(RagErrorCode.LLM_UPSTREAM_ERROR,
                    "Failed to open LLM stream: " + e.getMessage(), e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RagException(RagErrorCode.LLM_UPSTREAM_ERROR,
                    "LLM returned HTTP " + response.statusCode() + ": " + readErrorBody(response));
        }

        String model = config.model();
        try (Stream<String> lines = response.body()) {
            Iterator<String> iterator = lines.iterator();
            while (iterator.hasNext()) {
                String line = iterator.next().trim();
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring("data:".length()).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    break;
                }
                JsonNode chunk = readChunk(data);
                JsonNode choices = chunk.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    String delta = choices.get(0).path("delta").path("content").asText("");
                    if (!delta.isEmpty()) {
                        onDelta.accept(delta);
                    }
                }
                if (chunk.hasNonNull("model")) {
                    model = chunk.get("model").asText(model);
                }
            }
        } catch (UncheckedIOException e) {
            throw new RagException(RagErrorCode.LLM_UPSTREAM_ERROR, "Failed while reading LLM stream", e);
        }
        onModel.accept(model);
    }

    private JsonNode readChunk(String data) {
        try {
            return objectMapper.readTree(data);
        } catch (JsonProcessingException e) {
            throw new RagException(RagErrorCode.LLM_MALFORMED_RESPONSE, "LLM streamed malformed JSON chunk", e);
        }
    }

    private String readErrorBody(HttpResponse<Stream<String>> response) {
        try (Stream<String> lines = response.body()) {
            String body = lines.limit(5).collect(Collectors.joining("\n"));
            return truncate(body);
        }
    }

    private HttpRequest request(ObjectNode payload) {
        return HttpRequest.newBuilder(URI.create(config.baseUrl() + "/chat/completions"))
                .timeout(config.timeout())
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
    }

    private ObjectNode payload(List<ChatPrompt> messages, boolean stream) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", config.model());
        payload.put("temperature", TEMPERATURE);
        payload.put("stream", stream);
        ArrayNode messageNodes = payload.putArray("messages");
        for (ChatPrompt message : messages) {
            ObjectNode node = messageNodes.addObject();
            node.put("role", message.role());
            node.put("content", message.content());
        }
        return payload;
    }

    private void ensureConfigured() {
        if (!config.configured()) {
            throw new RagException(RagErrorCode.LLM_NOT_CONFIGURED,
                    "BOOKMIND_LLM_API_KEY is not set; AI companion is unavailable.");
        }
    }

    private void ensureSuccess(int statusCode, String body) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        String message = "LLM returned HTTP " + statusCode + ": " + truncate(body);
        if (statusCode == 401 || statusCode == 403) {
            throw new RagException(RagErrorCode.LLM_NOT_CONFIGURED, message);
        }
        throw new RagException(RagErrorCode.LLM_UPSTREAM_ERROR, message);
    }

    private RagException timeout(Throwable cause) {
        return new RagException(RagErrorCode.LLM_TIMEOUT,
                "LLM request timed out after " + config.timeout().toMillis() + " ms", cause);
    }

    private String truncate(String text) {
        return text.length() <= ERROR_BODY_MAX_CHARS ? text : text.substring(0, ERROR_BODY_MAX_CHARS);
    }

    /**
     * One OpenAI-compatible chat message.
     *
     * @param role    {@code system}, {@code user} or {@code assistant}
     * @param content message body
     */
    public record ChatPrompt(String role, String content) {
        public ChatPrompt {
            Objects.requireNonNull(role, "role must not be null");
            Objects.requireNonNull(content, "content must not be null");
        }
    }

    /**
     * Non-streaming completion result.
     *
     * @param content generated answer text
     * @param model   model name reported by the endpoint
     */
    public record ChatCompletion(String content, String model) {
        public ChatCompletion {
            Objects.requireNonNull(content, "content must not be null");
            Objects.requireNonNull(model, "model must not be null");
        }
    }
}

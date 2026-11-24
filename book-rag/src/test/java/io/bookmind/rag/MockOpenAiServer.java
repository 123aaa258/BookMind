package io.bookmind.rag;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal OpenAI-compatible test stub backed by the JDK {@link HttpServer}.
 *
 * <p>Keeps the whole test suite fully offline and deterministic: no real API key or network
 * access is required. Supports success, SSE streaming and common failure modes.
 */
final class MockOpenAiServer implements AutoCloseable {

    enum Mode {
        SUCCESS,
        STREAM,
        RATE_LIMIT,
        SERVER_ERROR,
        MALFORMED,
        TIMEOUT
    }

    static final String MODEL = "mock-model";
    static final String ANSWER = "插件隔离通过独立类加载器实现 [1]。";
    static final String DELTA_ONE = "根据书中第一章的定义 [1]，";
    static final String DELTA_TWO = "插件隔离通过独立类加载器实现。";

    private final HttpServer server;
    private final ExecutorService executor;
    private volatile Mode mode = Mode.SUCCESS;
    private volatile String lastRequestBody = "";

    MockOpenAiServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "mock-openai-server");
            thread.setDaemon(true);
            return thread;
        });
        server.createContext("/v1/chat/completions", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    String lastRequestBody() {
        return lastRequestBody;
    }

    void mode(Mode mode) {
        this.mode = mode;
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        switch (mode) {
            case RATE_LIMIT -> respond(exchange, 429, "{\"error\":{\"message\":\"rate limited\"}}");
            case SERVER_ERROR -> respond(exchange, 500, "{\"error\":{\"message\":\"boom\"}}");
            case MALFORMED -> respond(exchange, 200, "{\"choices\":[");
            case TIMEOUT -> {
                sleep();
                respond(exchange, 200, syncBody());
            }
            case STREAM -> stream(exchange);
            default -> respond(exchange, 200, syncBody());
        }
    }

    private void stream(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream;charset=UTF-8");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream output = exchange.getResponseBody()) {
            write(output, "data: {\"model\":\"" + MODEL + "\",\"choices\":[{\"delta\":{\"content\":\""
                    + DELTA_ONE + "\"}}]}\n\n");
            write(output, "data: {\"model\":\"" + MODEL + "\",\"choices\":[{\"delta\":{\"content\":\""
                    + DELTA_TWO + "\"}}]}\n\n");
            write(output, "data: [DONE]\n\n");
        }
    }

    private String syncBody() {
        return "{\"model\":\"" + MODEL + "\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                + ANSWER + "\"}}]}";
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void write(OutputStream output, String text) throws IOException {
        output.write(text.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private void sleep() {
        try {
            Thread.sleep(3_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}

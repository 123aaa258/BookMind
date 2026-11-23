package io.bookmind.server.api;

import io.bookmind.rag.RagErrorCode;
import io.bookmind.rag.RagException;
import io.bookmind.rag.RagService;
import io.bookmind.rag.RagStreamListener;
import io.bookmind.rag.model.Citation;
import io.bookmind.rag.model.RagAnswer;
import io.bookmind.rag.model.RagAskRequest;
import io.bookmind.rag.model.SearchHit;
import io.bookmind.server.config.BookMindProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * REST and Streamable HTTP (SSE over HTTP/1.1 chunked transfer) endpoints for local Lucene
 * search and OpenAI-compatible RAG companion answers.
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private static final int MAX_TOP_K = 50;

    private final RagService ragService;
    private final BookMindProperties properties;
    private final ExecutorService streamExecutor;

    public RagController(RagService ragService, BookMindProperties properties) {
        this.ragService = ragService;
        this.properties = properties;
        AtomicInteger counter = new AtomicInteger(1);
        this.streamExecutor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "bookmind-rag-sse-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    @GetMapping("/search")
    public List<SearchHit> search(
            @RequestParam("q") String query,
            @RequestParam(name = "bookId", required = false) String bookId,
            @RequestParam(name = "topK", required = false) Integer topK
    ) {
        if (query == null || query.isBlank()) {
            throw new RagException(RagErrorCode.INVALID_QUESTION, "Search query 'q' must not be blank");
        }
        return ragService.search(query.trim(), normalizeOptional(bookId), resolveTopK(topK));
    }

    @PostMapping("/ask")
    public RagAnswer ask(@RequestBody RagAskHttpRequest body) {
        return ragService.ask(toRagAskRequest(body));
    }

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestBody RagAskHttpRequest body) {
        RagAskRequest request = toRagAskRequest(body);
        long timeoutMs = Math.max(properties.getRag().getTimeoutSeconds(), 10) * 1000L + 15000L;
        SseEmitter emitter = new SseEmitter(timeoutMs);
        CompletableFuture<Void> streamStarted = new CompletableFuture<>();

        RagStreamListener listener = new RagStreamListener() {
            @Override
            public void onCitations(List<Citation> citations) {
                sendEvent(emitter, "citations", citations);
                streamStarted.complete(null);
            }

            @Override
            public void onDelta(String textDelta) {
                if (!streamStarted.isDone()) {
                    streamStarted.complete(null);
                }
                sendEvent(emitter, "delta", Map.of("content", textDelta));
            }

            @Override
            public void onComplete(String model) {
                if (!streamStarted.isDone()) {
                    streamStarted.complete(null);
                }
                sendEvent(emitter, "done", Map.of("model", model == null ? "" : model));
                emitter.complete();
            }

            @Override
            public void onError(RagException error) {
                if (!streamStarted.isDone()) {
                    streamStarted.completeExceptionally(error);
                    return;
                }
                sendEvent(emitter, "error", Map.of(
                        "code", error.errorCode().name(),
                        "message", error.getMessage() == null ? "" : error.getMessage()
                ));
                emitter.complete();
            }
        };

        streamExecutor.submit(() -> {
            try {
                ragService.askStream(request, listener);
                if (!streamStarted.isDone()) {
                    streamStarted.complete(null);
                }
            } catch (RagException ex) {
                if (!streamStarted.isDone()) {
                    streamStarted.completeExceptionally(ex);
                } else {
                    sendEvent(emitter, "error", Map.of(
                            "code", ex.errorCode().name(),
                            "message", ex.getMessage() == null ? "" : ex.getMessage()
                    ));
                    emitter.complete();
                }
            } catch (Throwable ex) {
                RagException wrapped = new RagException(
                        RagErrorCode.LLM_UPSTREAM_ERROR,
                        "Unexpected streaming error: " + ex.getMessage(),
                        ex
                );
                if (!streamStarted.isDone()) {
                    streamStarted.completeExceptionally(wrapped);
                } else {
                    sendEvent(emitter, "error", Map.of(
                            "code", wrapped.errorCode().name(),
                            "message", wrapped.getMessage()
                    ));
                    emitter.complete();
                }
            }
        });

        try {
            streamStarted.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RagException ragEx) {
                throw ragEx;
            }
            if (cause instanceof RuntimeException runtimeEx) {
                throw runtimeEx;
            }
            throw new RagException(RagErrorCode.LLM_UPSTREAM_ERROR, "Failed to initiate RAG stream", cause);
        }

        return emitter;
    }

    @PreDestroy
    public void shutdown() {
        streamExecutor.shutdownNow();
    }

    private RagAskRequest toRagAskRequest(RagAskHttpRequest body) {
        if (body == null || body.question() == null || body.question().isBlank()) {
            throw new RagException(RagErrorCode.INVALID_QUESTION, "Question must not be blank");
        }
        return new RagAskRequest(
                body.question().trim(),
                normalizeOptional(body.bookId()),
                resolveTopK(body.topK()),
                body.history() == null ? List.of() : body.history()
        );
    }

    private int resolveTopK(Integer requested) {
        int value = requested == null ? properties.getRag().getTopK() : requested;
        if (value < 1) {
            throw new IllegalArgumentException("topK must be >= 1");
        }
        return Math.min(value, MAX_TOP_K);
    }

    private static Optional<String> normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }

    private static void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ignored) {
            emitter.complete();
        }
    }
}

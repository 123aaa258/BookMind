package io.bookmind.rag;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Composition root for the RAG engine.
 *
 * <p>{@code book-server} only needs to call this factory (for example from a Spring
 * {@code @Bean} method) and never has to know how Lucene or the HTTP client are assembled.
 */
public final class RagEngineFactory {

    private RagEngineFactory() {
    }

    /**
     * Creates a fully wired RAG engine.
     *
     * @param indexPath Lucene index directory, typically {@code bookmind.data-dir/lucene-index}
     * @param config    OpenAI-compatible endpoint configuration
     * @return engine that should be closed when the server shuts down
     * @throws IOException when the Lucene index cannot be opened
     */
    public static DefaultRagService create(Path indexPath, OpenAiConfig config) throws IOException {
        Objects.requireNonNull(indexPath, "indexPath must not be null");
        Objects.requireNonNull(config, "config must not be null");
        return new DefaultRagService(new LuceneBookIndex(indexPath), new OpenAiChatClient(config));
    }

    /**
     * Creates an engine configured from {@code BOOKMIND_LLM_*} environment variables.
     *
     * @param indexPath Lucene index directory
     * @return engine that should be closed when the server shuts down
     * @throws IOException when the Lucene index cannot be opened
     */
    public static DefaultRagService fromEnvironment(Path indexPath) throws IOException {
        return create(indexPath, OpenAiConfig.fromEnvironment());
    }
}

package io.bookmind.rag;

import io.bookmind.rag.OpenAiChatClient.ChatCompletion;
import io.bookmind.rag.OpenAiChatClient.ChatPrompt;
import io.bookmind.rag.model.ChatMessage;
import io.bookmind.rag.model.Citation;
import io.bookmind.rag.model.RagAnswer;
import io.bookmind.rag.model.RagAskRequest;
import io.bookmind.rag.model.SearchHit;
import io.bookmind.spi.ParsedBook;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Default RAG orchestration: local Lucene retrieval followed by an OpenAI-compatible answer.
 *
 * <p>The generated answer and the grounded citations are always returned as separate fields,
 * so the frontend can render book evidence independently and jump back to the source locator.
 */
public class DefaultRagService implements RagService, Closeable {

    private static final int EXCERPT_MAX_CHARS = 200;

    private final LuceneBookIndex index;
    private final OpenAiChatClient client;

    public DefaultRagService(LuceneBookIndex index, OpenAiChatClient client) {
        this.index = Objects.requireNonNull(index, "index must not be null");
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    @Override
    public void indexBook(String bookId, ParsedBook book) {
        index.indexBook(bookId, book);
    }

    @Override
    public void removeBook(String bookId) {
        index.removeBook(bookId);
    }

    @Override
    public List<SearchHit> search(String query, Optional<String> optionalBookId, int topK) {
        return index.search(query, optionalBookId, topK);
    }

    @Override
    public RagAnswer ask(RagAskRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ensureConfigured();
        List<Citation> citations = retrieveCitations(request);
        ChatCompletion completion = client.complete(prompt(request, citations));
        return new RagAnswer(request.question(), completion.content(), completion.model(), citations);
    }

    @Override
    public void askStream(RagAskRequest request, RagStreamListener listener) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        ensureConfigured();
        List<Citation> citations = retrieveCitations(request);
        listener.onCitations(citations);
        try {
            client.stream(prompt(request, citations), listener::onDelta, listener::onComplete);
        } catch (RagException e) {
            listener.onError(e);
        }
    }

    @Override
    public void close() throws IOException {
        index.close();
    }

    private void ensureConfigured() {
        if (!client.configured()) {
            throw new RagException(RagErrorCode.LLM_NOT_CONFIGURED,
                    "BOOKMIND_LLM_API_KEY is not set; AI companion is unavailable.");
        }
    }

    private List<Citation> retrieveCitations(RagAskRequest request) {
        List<SearchHit> hits = index.search(request.question(), request.bookId(), request.topK());
        if (hits.isEmpty()) {
            throw new RagException(RagErrorCode.NO_EVIDENCE_FOUND,
                    "No book evidence matched the question: " + request.question());
        }
        List<Citation> citations = new ArrayList<>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            SearchHit hit = hits.get(i);
            citations.add(new Citation(
                    i + 1,
                    hit.bookId(),
                    hit.bookTitle(),
                    hit.chapterIndex(),
                    hit.chapterTitle(),
                    hit.locator(),
                    excerpt(hit.snippet()),
                    hit.score()
            ));
        }
        return List.copyOf(citations);
    }

    private List<ChatPrompt> prompt(RagAskRequest request, List<Citation> citations) {
        List<ChatPrompt> messages = new ArrayList<>();
        messages.add(new ChatPrompt("system", systemPrompt(citations)));
        for (ChatMessage history : request.history()) {
            messages.add(new ChatPrompt(history.role(), history.content()));
        }
        messages.add(new ChatPrompt("user", request.question()));
        return List.copyOf(messages);
    }

    private String systemPrompt(List<Citation> citations) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是 BookMind 的 AI 伴读助手。只能依据下面列出的书籍原文证据回答，不得编造证据之外的内容。\n");
        builder.append("引用规则：\n");
        builder.append("1. 引用证据时必须使用 [序号] 标注，例如 [1]、[2]。\n");
        builder.append("2. 证据不足时直接回答“书中未找到足够证据”，不要臆测。\n");
        builder.append("3. 使用与提问相同的语言，回答简洁准确。\n\n");
        builder.append("【书籍原文证据】\n");
        for (Citation citation : citations) {
            builder.append('[').append(citation.refNumber()).append("] 《").append(citation.bookTitle())
                    .append("》 ").append(citation.chapterTitle())
                    .append("（locator: ").append(citation.locator()).append("）\n")
                    .append(citation.excerpt()).append("\n\n");
        }
        return builder.toString();
    }

    private String excerpt(String snippet) {
        String plain = snippet.replace("<mark>", "").replace("</mark>", "");
        return plain.length() <= EXCERPT_MAX_CHARS ? plain : plain.substring(0, EXCERPT_MAX_CHARS);
    }
}

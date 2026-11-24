package io.bookmind.rag;

import io.bookmind.rag.model.ChatMessage;
import io.bookmind.rag.model.Citation;
import io.bookmind.rag.model.RagAnswer;
import io.bookmind.rag.model.RagAskRequest;
import io.bookmind.spi.Chapter;
import io.bookmind.spi.ParsedBook;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultRagServiceTest {

    private LuceneBookIndex index;
    private MockOpenAiServer server;
    private DefaultRagService service;

    @BeforeEach
    void setUp() throws IOException {
        index = new LuceneBookIndex(new ByteBuffersDirectory(), new ChapterChunker());
        index.indexBook("b1", new ParsedBook("开源软件生态与开发技术", "BookMind 示例编委会", "zh", "txt",
                Optional.empty(),
                List.of(new Chapter(0, "第一章 插件体系", "chapter:0",
                        "插件隔离通过独立的类加载器实现，保证核心模块与插件互不干扰。")),
                Map.of()));
        server = new MockOpenAiServer();
        service = new DefaultRagService(index, new OpenAiChatClient(config(Duration.ofSeconds(2))));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.close();
        index.close();
    }

    private OpenAiConfig config(Duration timeout) {
        return new OpenAiConfig(server.baseUrl(), "test-key", "mock-model", timeout);
    }

    private RagAskRequest question() {
        return RagAskRequest.simple("插件隔离是怎么实现的？", Optional.of("b1"), 3);
    }

    @Test
    void askReturnsGeneratedAnswerWithSeparateGroundedCitations() {
        RagAnswer answer = service.ask(question());

        assertThat(answer.answer()).isEqualTo(MockOpenAiServer.ANSWER).contains("[1]");
        assertThat(answer.model()).isEqualTo(MockOpenAiServer.MODEL);
        assertThat(answer.citations()).hasSize(1);

        Citation citation = answer.citations().get(0);
        assertThat(citation.refNumber()).isEqualTo(1);
        assertThat(citation.bookId()).isEqualTo("b1");
        assertThat(citation.bookTitle()).isEqualTo("开源软件生态与开发技术");
        assertThat(citation.chapterTitle()).isEqualTo("第一章 插件体系");
        assertThat(citation.locator()).isEqualTo("chapter:0");
        assertThat(citation.excerpt()).contains("插件隔离").doesNotContain("<mark>");
        assertThat(citation.score()).isPositive();

        assertThat(server.lastRequestBody())
                .contains("\"stream\":false")
                .contains("[1]")
                .contains("插件隔离是怎么实现的");
    }

    @Test
    void askForwardsMultiTurnHistoryToTheModel() {
        RagAskRequest request = new RagAskRequest("那类加载器呢？", Optional.of("b1"), 3,
                List.of(
                        new ChatMessage("user", "上一轮问题：什么是插件隔离？"),
                        new ChatMessage("assistant", "上一轮回答：通过独立类加载器实现 [1]。")
                ));

        service.ask(request);

        assertThat(server.lastRequestBody())
                .contains("上一轮问题：什么是插件隔离？")
                .contains("上一轮回答：通过独立类加载器实现 [1]。")
                .contains("\"role\":\"assistant\"");
    }

    @Test
    void missingApiKeyFailsWithNotConfiguredAndNeverCallsUpstream() {
        DefaultRagService unconfigured = new DefaultRagService(index,
                new OpenAiChatClient(new OpenAiConfig(server.baseUrl(), "", "mock-model", Duration.ofSeconds(2))));

        assertThatThrownBy(() -> unconfigured.ask(question()))
                .isInstanceOf(RagException.class)
                .extracting(error -> ((RagException) error).errorCode())
                .isEqualTo(RagErrorCode.LLM_NOT_CONFIGURED);
        assertThat(server.lastRequestBody()).isEmpty();
    }

    @Test
    void questionWithoutEvidenceFailsWithNoEvidenceFound() {
        RagAskRequest request = RagAskRequest.simple("quantumblockchainzxqv", Optional.empty(), 3);

        assertThatThrownBy(() -> service.ask(request))
                .isInstanceOf(RagException.class)
                .extracting(error -> ((RagException) error).errorCode())
                .isEqualTo(RagErrorCode.NO_EVIDENCE_FOUND);
    }

    @Test
    void rateLimitIsMappedToUpstreamError() {
        server.mode(MockOpenAiServer.Mode.RATE_LIMIT);

        assertThatThrownBy(() -> service.ask(question()))
                .isInstanceOf(RagException.class)
                .extracting(error -> ((RagException) error).errorCode())
                .isEqualTo(RagErrorCode.LLM_UPSTREAM_ERROR);
    }

    @Test
    void malformedJsonIsMappedToMalformedResponse() {
        server.mode(MockOpenAiServer.Mode.MALFORMED);

        assertThatThrownBy(() -> service.ask(question()))
                .isInstanceOf(RagException.class)
                .extracting(error -> ((RagException) error).errorCode())
                .isEqualTo(RagErrorCode.LLM_MALFORMED_RESPONSE);
    }

    @Test
    void upstreamTimeoutIsMappedToLlmTimeout() {
        server.mode(MockOpenAiServer.Mode.TIMEOUT);
        DefaultRagService fastTimeout = new DefaultRagService(index,
                new OpenAiChatClient(config(Duration.ofMillis(300))));

        assertThatThrownBy(() -> fastTimeout.ask(question()))
                .isInstanceOf(RagException.class)
                .extracting(error -> ((RagException) error).errorCode())
                .isEqualTo(RagErrorCode.LLM_TIMEOUT);
    }

    @Test
    void askStreamEmitsCitationsThenDeltasThenDone() {
        server.mode(MockOpenAiServer.Mode.STREAM);
        RecordingListener listener = new RecordingListener();

        service.askStream(question(), listener);

        assertThat(listener.events).containsExactly("citations:1", "delta", "delta", "done");
        assertThat(listener.text.toString()).isEqualTo(MockOpenAiServer.DELTA_ONE + MockOpenAiServer.DELTA_TWO);
        assertThat(listener.model).isEqualTo(MockOpenAiServer.MODEL);
        assertThat(listener.error).isNull();
        assertThat(server.lastRequestBody()).contains("\"stream\":true").contains("[1]");
    }

    @Test
    void askStreamWithoutApiKeyThrowsBeforeEmittingAnyEvent() {
        DefaultRagService unconfigured = new DefaultRagService(index,
                new OpenAiChatClient(new OpenAiConfig(server.baseUrl(), "", "mock-model", Duration.ofSeconds(2))));
        RecordingListener listener = new RecordingListener();

        assertThatThrownBy(() -> unconfigured.askStream(question(), listener))
                .isInstanceOf(RagException.class)
                .extracting(error -> ((RagException) error).errorCode())
                .isEqualTo(RagErrorCode.LLM_NOT_CONFIGURED);
        assertThat(listener.events).isEmpty();
    }

    @Test
    void askStreamMidStreamFailureIsReportedToTheListener() {
        server.mode(MockOpenAiServer.Mode.SERVER_ERROR);
        RecordingListener listener = new RecordingListener();

        service.askStream(question(), listener);

        assertThat(listener.events).containsExactly("citations:1", "error");
        assertThat(listener.error).isNotNull();
        assertThat(listener.error.errorCode()).isEqualTo(RagErrorCode.LLM_UPSTREAM_ERROR);
    }

    private static final class RecordingListener implements RagStreamListener {

        private final List<String> events = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();
        private String model;
        private RagException error;

        @Override
        public void onCitations(List<Citation> citations) {
            events.add("citations:" + citations.size());
        }

        @Override
        public void onDelta(String textDelta) {
            events.add("delta");
            text.append(textDelta);
        }

        @Override
        public void onComplete(String completedModel) {
            events.add("done");
            this.model = completedModel;
        }

        @Override
        public void onError(RagException ragException) {
            events.add("error");
            this.error = ragException;
        }
    }
}

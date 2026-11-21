package io.bookmind.rag;

import io.bookmind.rag.model.ChatMessage;
import io.bookmind.rag.model.Citation;
import io.bookmind.rag.model.RagAnswer;
import io.bookmind.rag.model.RagAskRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagContractTest {

    @Test
    void ragAnswerSeparatesGeneratedAnswerAndCitations() {
        Citation citation = new Citation(
                1, "book-1", "开源导论", 0, "第一章", "chapter:1", "核心契约与插件隔离", 2.5f
        );
        RagAnswer answer = new RagAnswer("什么是插件隔离？", "插件隔离通过独立类加载器实现 [1]。", "gpt-4o-mini",
                List.of(citation));

        assertThat(answer.answer()).contains("[1]");
        assertThat(answer.citations()).hasSize(1);
        assertThat(answer.citations().get(0).locator()).isEqualTo("chapter:1");
    }

    @Test
    void askRequestSupportsOptionalMultiTurnHistory() {
        RagAskRequest request = new RagAskRequest(
                "那故障隔离怎么做？",
                Optional.of("book-1"),
                5,
                List.of(
                        new ChatMessage("user", "什么是插件隔离？"),
                        new ChatMessage("assistant", "通过独立 URLClassLoader 实现 [1]。")
                )
        );
        assertThat(request.history()).hasSize(2);
        assertThat(request.bookId()).contains("book-1");
    }

    @Test
    void bootstrapServiceReportsNotConfiguredForBothSyncAndStreamAsk() {
        RagService service = new BootstrapRagService();
        RagAskRequest request = RagAskRequest.simple("什么是 SPI？", Optional.empty(), 5);

        assertThat(service.search("test", Optional.empty(), 5)).isEmpty();
        assertThatThrownBy(() -> service.ask(request))
                .isInstanceOf(RagException.class)
                .extracting(ex -> ((RagException) ex).errorCode())
                .isEqualTo(RagErrorCode.LLM_NOT_CONFIGURED);

        RagStreamListener noopListener = new RagStreamListener() {
            @Override
            public void onCitations(List<Citation> citations) {
            }

            @Override
            public void onDelta(String textDelta) {
            }

            @Override
            public void onComplete(String model) {
            }

            @Override
            public void onError(RagException error) {
            }
        };
        assertThatThrownBy(() -> service.askStream(request, noopListener))
                .isInstanceOf(RagException.class)
                .extracting(ex -> ((RagException) ex).errorCode())
                .isEqualTo(RagErrorCode.LLM_NOT_CONFIGURED);
    }
}

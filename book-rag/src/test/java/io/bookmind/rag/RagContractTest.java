package io.bookmind.rag;

import io.bookmind.rag.model.Citation;
import io.bookmind.rag.model.RagAnswer;
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
    void bootstrapServiceReportsNotConfiguredError() {
        RagService service = new BootstrapRagService();
        assertThat(service.search("test", Optional.empty(), 5)).isEmpty();
        assertThatThrownBy(() -> service.ask("什么是 SPI？", Optional.empty(), 5))
                .isInstanceOf(RagException.class)
                .extracting(ex -> ((RagException) ex).errorCode())
                .isEqualTo(RagErrorCode.LLM_NOT_CONFIGURED);
    }
}

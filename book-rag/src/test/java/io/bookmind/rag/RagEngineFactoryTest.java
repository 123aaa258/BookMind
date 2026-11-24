package io.bookmind.rag;

import io.bookmind.spi.Chapter;
import io.bookmind.spi.ParsedBook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RagEngineFactoryTest {

    @Test
    void factoryCreatesFileSystemIndexWhoseContentSurvivesReopen(@TempDir Path tempDir) throws IOException {
        Path indexPath = tempDir.resolve("lucene-index");
        OpenAiConfig config = new OpenAiConfig("http://127.0.0.1:1/v1", "", "mock-model", Duration.ofSeconds(1));

        try (DefaultRagService engine = RagEngineFactory.create(indexPath, config)) {
            engine.indexBook("b1", book());
            assertThat(engine.search("插件隔离", Optional.empty(), 5)).hasSize(1);
        }

        try (DefaultRagService reopened = RagEngineFactory.create(indexPath, config)) {
            assertThat(reopened.search("插件隔离", Optional.empty(), 5)).hasSize(1);
        }
    }

    private static ParsedBook book() {
        return new ParsedBook("开源导论", "BookMind 示例编委会", "zh", "txt", Optional.empty(),
                List.of(new Chapter(0, "第一章", "chapter:0", "插件隔离通过类加载器实现。")), Map.of());
    }
}

package io.bookmind.rag;

import io.bookmind.rag.model.TextChunk;
import io.bookmind.spi.Chapter;
import io.bookmind.spi.ParsedBook;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChapterChunkerTest {

    private static ParsedBook bookOf(Chapter... chapters) {
        return new ParsedBook("RAG 系统工程简明手册", "BookMind 示例编委会", "zh", "txt",
                Optional.empty(), List.of(chapters), Map.of());
    }

    @Test
    void shortChapterProducesSingleChunkWithPreservedMetadata() {
        Chapter chapter = new Chapter(2, "第二章 检索增强生成", "chapter:2", "检索增强生成先召回原文再生成回答。");
        ParsedBook book = bookOf(chapter);

        List<TextChunk> chunks = new ChapterChunker().chunk("book-1", book);

        assertThat(chunks).hasSize(1);
        TextChunk chunk = chunks.get(0);
        assertThat(chunk.chunkId()).isEqualTo("book-1:2:0");
        assertThat(chunk.bookId()).isEqualTo("book-1");
        assertThat(chunk.bookTitle()).isEqualTo("RAG 系统工程简明手册");
        assertThat(chunk.chapterIndex()).isEqualTo(2);
        assertThat(chunk.chapterTitle()).isEqualTo("第二章 检索增强生成");
        assertThat(chunk.locator()).isEqualTo("chapter:2");
        assertThat(chunk.text()).isEqualTo("检索增强生成先召回原文再生成回答。");
    }

    @Test
    void longChapterIsSplitIntoOverlappingWindows() {
        String text = "0123456789".repeat(120);
        Chapter chapter = new Chapter(0, "第一章", "chapter:0", text);

        List<TextChunk> chunks = new ChapterChunker(500, 80).chunk("book-1", bookOf(chapter));

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).text()).hasSize(500);
        assertThat(chunks.get(1).text()).hasSize(500);
        assertThat(chunks.get(2).text()).hasSize(360);
        assertThat(chunks.get(0).text().substring(420)).isEqualTo(chunks.get(1).text().substring(0, 80));
        assertThat(chunks.get(1).text().substring(420)).isEqualTo(chunks.get(2).text().substring(0, 80));
        assertThat(chunks.get(0).chunkId()).isEqualTo("book-1:0:0");
        assertThat(chunks.get(1).chunkId()).isEqualTo("book-1:0:1");
        assertThat(chunks.get(2).chunkId()).isEqualTo("book-1:0:2");
    }

    @Test
    void blankChaptersAreSkippedButOtherChaptersStayOrdered() {
        ParsedBook book = bookOf(
                new Chapter(0, "空白章", "chapter:0", "   \n\t "),
                new Chapter(1, "有效章", "chapter:1", "Lucene BM25 检索")
        );

        List<TextChunk> chunks = new ChapterChunker().chunk("book-9", book);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).chapterIndex()).isEqualTo(1);
        assertThat(chunks.get(0).locator()).isEqualTo("chapter:1");
    }

    @Test
    void chunkIdsAreUniqueAcrossChapters() {
        ParsedBook book = bookOf(
                new Chapter(0, "第一章", "chapter:0", "a".repeat(600)),
                new Chapter(1, "第二章", "chapter:1", "b".repeat(600))
        );

        List<TextChunk> chunks = new ChapterChunker(500, 80).chunk("book-1", book);

        assertThat(chunks).extracting(TextChunk::chunkId).doesNotHaveDuplicates();
        assertThat(chunks).extracting(TextChunk::chapterIndex).containsExactly(0, 0, 1, 1);
    }

    @Test
    void invalidWindowConfigurationIsRejected() {
        assertThatThrownBy(() -> new ChapterChunker(0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunkSize");
        assertThatThrownBy(() -> new ChapterChunker(100, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
        assertThatThrownBy(() -> new ChapterChunker(100, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
    }
}

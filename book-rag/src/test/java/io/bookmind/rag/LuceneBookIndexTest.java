package io.bookmind.rag;

import io.bookmind.rag.model.SearchHit;
import io.bookmind.spi.Chapter;
import io.bookmind.spi.ParsedBook;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LuceneBookIndexTest {

    private LuceneBookIndex index;

    @BeforeEach
    void setUp() throws IOException {
        index = new LuceneBookIndex(new ByteBuffersDirectory(), new ChapterChunker());
    }

    @AfterEach
    void tearDown() throws IOException {
        index.close();
    }

    private static ParsedBook book(String title, Chapter... chapters) {
        return new ParsedBook(title, "BookMind 示例编委会", "zh", "txt",
                Optional.empty(), List.of(chapters), Map.of());
    }

    private static Chapter chapter(int index, String title, String locator, String content) {
        return new Chapter(index, title, locator, content);
    }

    private static String plain(String snippet) {
        return snippet.replace("<mark>", "").replace("</mark>", "");
    }

    @Test
    void chineseQueryHitsExpectedBookChapterAndLocator() {
        index.indexBook("b1", book("开源软件生态与开发技术",
                chapter(0, "第一章 总论", "chapter:0", "插件隔离通过独立的类加载器实现，保证核心模块与插件互不干扰。"),
                chapter(1, "第二章 检索", "chapter:1", "Lucene BM25 负责本地全文检索。")));
        index.indexBook("b2", book("前端设计手册",
                chapter(0, "第一章 界面", "page:0", "React 组件与动效设计。")));

        List<SearchHit> hits = index.search("插件隔离", Optional.empty(), 5);

        assertThat(hits).isNotEmpty();
        SearchHit top = hits.get(0);
        assertThat(top.bookId()).isEqualTo("b1");
        assertThat(top.bookTitle()).isEqualTo("开源软件生态与开发技术");
        assertThat(top.chapterTitle()).isEqualTo("第一章 总论");
        assertThat(top.locator()).isEqualTo("chapter:0");
        assertThat(plain(top.snippet())).contains("插件隔离");
        assertThat(top.score()).isPositive();
    }

    @Test
    void bookTitleIsSearchableAndCarriesBookId() {
        index.indexBook("b2", book("前端设计手册",
                chapter(0, "第一章 界面", "chapter:0", "React 组件与动效设计。")));
        index.indexBook("b1", book("开源软件生态与开发技术",
                chapter(0, "第一章 总论", "chapter:0", "插件隔离与类加载器。")));

        List<SearchHit> hits = index.search("前端设计手册", Optional.empty(), 5);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).bookId()).isEqualTo("b2");
        assertThat(hits.get(0).bookTitle()).isEqualTo("前端设计手册");
    }

    /**
     * Mutation demo: this test pins BM25 relevance. Replacing BM25 with constant scoring,
     * reversing the hit order, or dropping term frequency would break this assertion,
     * which proves the test is not merely asserting a non-empty result.
     */
    @Test
    void rankingIsPinnedSoScoreMutationIsCaught() {
        index.indexBook("b-heavy", book("检索工程",
                chapter(0, "第一章", "chapter:0", "BM25 BM25 BM25 BM25 BM25 检索")));
        index.indexBook("b-light", book("检索入门",
                chapter(0, "第一章", "chapter:0", "BM25 检索")));

        List<SearchHit> hits = index.search("BM25", Optional.empty(), 5);

        assertThat(hits).extracting(SearchHit::bookId).containsExactly("b-heavy", "b-light");
    }

    @Test
    void bookIdFilterRestrictsHitsToSingleBook() {
        index.indexBook("b1", book("书一", chapter(0, "第一章", "chapter:0", "统一检索与插件隔离。")));
        index.indexBook("b2", book("书二", chapter(0, "第一章", "chapter:0", "统一检索与流式回答。")));

        List<SearchHit> all = index.search("检索", Optional.empty(), 5);
        List<SearchHit> scoped = index.search("检索", Optional.of("b2"), 5);

        assertThat(all).extracting(SearchHit::bookId).contains("b1", "b2");
        assertThat(scoped).isNotEmpty();
        assertThat(scoped).extracting(SearchHit::bookId).containsOnly("b2");
    }

    @Test
    void reindexingSameBookDoesNotDuplicateChunks() {
        ParsedBook parsed = book("开源导论", chapter(0, "第一章", "chapter:0", "插件隔离通过类加载器实现。"));

        index.indexBook("b1", parsed);
        index.indexBook("b1", parsed);

        assertThat(index.search("插件隔离", Optional.of("b1"), 5)).hasSize(1);
    }

    @Test
    void removeBookDeletesAllItsChunks() {
        index.indexBook("b1", book("书一", chapter(0, "第一章", "chapter:0", "插件隔离通过类加载器实现。")));

        index.removeBook("b1");

        assertThat(index.search("插件隔离", Optional.empty(), 5)).isEmpty();
    }

    @Test
    void luceneSpecialCharactersInQueryDoNotCrashTheSearch() {
        index.indexBook("b1", book("书一", chapter(0, "第一章", "chapter:0", "BM25 检索与插件隔离。")));

        assertThatCode(() -> index.search("BM25: 检索 [1] +- && ( ) { } ^ \" ~ * ?", Optional.empty(), 5))
                .doesNotThrowAnyException();
    }

    @Test
    void blankQueryReturnsEmptyAndInvalidTopKIsRejected() {
        index.indexBook("b1", book("书一", chapter(0, "第一章", "chapter:0", "BM25 检索。")));

        assertThat(index.search("  ", Optional.empty(), 5)).isEmpty();
        assertThatThrownBy(() -> index.search("检索", Optional.empty(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topK");
    }

    @Test
    void concurrentSearchesStayConsistentWhileTheBookIsReindexed() throws Exception {
        ParsedBook parsed = book("书一", chapter(0, "第一章", "chapter:0", "插件隔离通过类加载器实现。"));
        index.indexBook("b1", parsed);

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<List<SearchHit>>> reads = new ArrayList<>();
            List<Future<?>> writes = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                reads.add(pool.submit(() -> index.search("插件隔离", Optional.empty(), 5)));
            }
            for (int i = 0; i < 5; i++) {
                writes.add(pool.submit(() -> index.indexBook("b1", parsed)));
            }
            for (Future<List<SearchHit>> read : reads) {
                assertThat(read.get(10, TimeUnit.SECONDS)).hasSize(1);
            }
            for (Future<?> write : writes) {
                write.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}

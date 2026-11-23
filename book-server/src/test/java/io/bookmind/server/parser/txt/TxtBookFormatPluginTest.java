package io.bookmind.server.parser.txt;

import io.bookmind.spi.BookFormatPlugin;
import io.bookmind.spi.ParsedBook;
import io.bookmind.spi.PluginErrorCode;
import io.bookmind.spi.PluginException;
import io.bookmind.tck.AbstractBookFormatPluginContractTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TxtBookFormatPluginTest extends AbstractBookFormatPluginContractTest {

    @Override
    protected BookFormatPlugin createPlugin() {
        return new TxtBookFormatPlugin();
    }

    @Override
    protected Path validSampleFile(Path tempDir) throws Exception {
        Path sample = tempDir.resolve("valid-sample.txt");
        Files.writeString(sample, """
                《测试书名》
                作者：测试作者

                第一章 起步
                这是第一章的正文内容。

                第二章 进阶
                这是第二章的正文内容。
                """, StandardCharsets.UTF_8);
        return sample;
    }

    @Override
    protected Path corruptedSampleFile(Path tempDir) throws Exception {
        Path corrupt = tempDir.resolve("corrupt.txt");
        Files.write(corrupt, new byte[] { 0x50, 0x4B, 0x00, 0x00, 0x14, 0x00 });
        return corrupt;
    }

    @Test
    void parsesRepositorySampleBookWithExactTitleAuthorAndChapters() throws Exception {
        Path sample = Path.of("..", "samples", "books", "sample-oss-guide.txt");
        TxtBookFormatPlugin plugin = new TxtBookFormatPlugin();

        ParsedBook book = plugin.parse(sample);

        assertThat(book.title()).isEqualTo("开源软件工程实战简明读本");
        assertThat(book.author()).isEqualTo("BookMind 示例编委会");
        assertThat(book.language()).isEqualTo("zh-CN");
        assertThat(book.format()).isEqualTo("txt");
        assertThat(book.chapters()).hasSize(3);
        assertThat(book.chapters().get(0).title()).isEqualTo("第一章 插件化架构与契约边界");
        assertThat(book.chapters().get(0).locator()).isEqualTo("chapter:1");
        assertThat(book.chapters().get(1).locator()).isEqualTo("chapter:2");
        assertThat(book.chapters().get(2).locator()).isEqualTo("chapter:3");
        assertThat(book.chapters().get(1).content()).contains("BM25");
    }

    @Test
    void decodesGb18030EncodedChineseTxtCleanly(@TempDir Path tempDir) throws Exception {
        Path gbkFile = tempDir.resolve("gbk-novel.txt");
        String content = "第一章 编码测试\n这是一段使用 GB18030 编码保存的中文内容。";
        Files.write(gbkFile, content.getBytes(Charset.forName("GB18030")));

        ParsedBook book = new TxtBookFormatPlugin().parse(gbkFile);

        assertThat(book.title()).isEqualTo("gbk-novel");
        assertThat(book.extraMetadata().get("encoding")).isEqualTo("GB18030");
        assertThat(book.chapters()).hasSize(1);
        assertThat(book.chapters().get(0).content()).contains("编码保存的中文内容");
    }

    @Test
    void splitsEnglishChaptersAndFallsBackToSingleChapterWhenNoHeadings(@TempDir Path tempDir) throws Exception {
        Path english = tempDir.resolve("english-guide.txt");
        Files.writeString(english, """
                Chapter 1 Introduction
                First chapter body.

                Chapter 2 Architecture
                Second chapter body.
                """, StandardCharsets.UTF_8);

        TxtBookFormatPlugin plugin = new TxtBookFormatPlugin();
        ParsedBook enBook = plugin.parse(english);
        assertThat(enBook.language()).isEqualTo("en");
        assertThat(enBook.chapters()).hasSize(2);
        assertThat(enBook.chapters().get(1).title()).isEqualTo("Chapter 2 Architecture");

        Path plain = tempDir.resolve("notes.txt");
        Files.writeString(plain, "这是一段没有任何章节标题的随笔内容。", StandardCharsets.UTF_8);
        ParsedBook plainBook = plugin.parse(plain);
        assertThat(plainBook.chapters()).hasSize(1);
        assertThat(plainBook.chapters().get(0).title()).isEqualTo("全文");
        assertThat(plainBook.chapters().get(0).locator()).isEqualTo("chapter:1");
    }

    @Test
    void rejectsWhitespaceOnlyFileWithEmptyContentError(@TempDir Path tempDir) throws Exception {
        Path blank = tempDir.resolve("blank.txt");
        Files.writeString(blank, "   \n\t  \n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new TxtBookFormatPlugin().parse(blank))
                .isInstanceOf(PluginException.class)
                .extracting(ex -> ((PluginException) ex).errorCode())
                .isEqualTo(PluginErrorCode.EMPTY_CONTENT);
    }
}

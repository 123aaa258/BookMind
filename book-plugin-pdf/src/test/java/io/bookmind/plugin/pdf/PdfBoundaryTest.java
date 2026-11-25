package io.bookmind.plugin.pdf;

import io.bookmind.spi.Chapter;
import io.bookmind.spi.PluginErrorCode;
import io.bookmind.spi.PluginException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class PdfBoundaryTest {
    private static final Path SAMPLES = Path.of("..", "samples");
    private static final Path HANDBOOK = SAMPLES.resolve("books/sample-rag-handbook.pdf");
    private final PdfBookFormatPlugin plugin = new PdfBookFormatPlugin();

    @ParameterizedTest
    @ValueSource(strings = {"reader-password", ""})
    void reportsEncryptionEvenWhenTheReaderPasswordIsEmpty(String password, @TempDir Path dir) throws Exception {
        Path file = dir.resolve("encrypted.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.protect(new StandardProtectionPolicy("owner-password", password, new AccessPermission()));
            document.save(file.toFile());
        }
        assertError(file, PluginErrorCode.ENCRYPTED_DOCUMENT);
    }

    @Test
    void imageOnlyScanHasNoIndexableText(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("scan.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            BufferedImage pixels = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
            pixels.setRGB(1, 1, 0xffffff);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.drawImage(LosslessFactory.createFromImage(document, pixels), 50, 50, 100, 100);
            }
            document.save(file.toFile());
        }
        assertError(file, PluginErrorCode.EMPTY_CONTENT);
    }

    @Test
    void rejectsRepositorySampleWithInvalidHeader() {
        assertError(SAMPLES.resolve("corrupted/invalid-header.pdf"), PluginErrorCode.CORRUPTED_FILE);
    }

    @Test
    void rejectsDamagedHeaderEvenWhenThePageTreeIsIntact(@TempDir Path dir) throws Exception {
        byte[] bytes = Files.readAllBytes(HANDBOOK);
        bytes[1] = 'X';
        Path file = Files.write(dir.resolve("bad-signature.pdf"), bytes);
        assertError(file, PluginErrorCode.CORRUPTED_FILE);
    }

    @Test
    void handbookProvidesAStableSearchTermOnTheSecondPage() throws Exception {
        var book = plugin.parse(HANDBOOK);
        assertThat(book.title()).isEqualTo("RAG 检索手册");
        assertThat(book.chapters()).extracting(Chapter::locator).containsExactly("page:1", "page:2");
        assertThat(book.chapters().getFirst().content()).doesNotContain("蓝鹭校验词");
        assertThat(book.chapters().getLast().content()).contains("蓝鹭校验词");
    }

    private void assertError(Path file, PluginErrorCode expected) {
        assertThatExceptionOfType(PluginException.class).isThrownBy(() -> plugin.parse(file))
                .extracting(PluginException::errorCode).isEqualTo(expected);
    }
}

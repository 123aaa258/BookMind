package io.bookmind.plugin.pdf;

import io.bookmind.spi.Chapter;
import io.bookmind.spi.ParsedBook;
import io.bookmind.tck.AbstractBookFormatPluginContractTest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PdfBookFormatPluginTest extends AbstractBookFormatPluginContractTest {

    @Override
    protected PdfBookFormatPlugin createPlugin() {
        return new PdfBookFormatPlugin();
    }

    @Override
    protected Path validSampleFile(Path tempDir) throws Exception {
        Path file = tempDir.resolve("course.notes.pdf");
        try (PDDocument document = new PDDocument()) {
            addPage(document, "Introduction");
            document.addPage(new PDPage());
            addPage(document, "Closing notes");
            document.save(file.toFile());
        }
        return file;
    }

    @Override
    protected Path corruptedSampleFile(Path tempDir) throws Exception {
        return Files.writeString(tempDir.resolve("broken.pdf"), "Not a PDF");
    }

    @Test
    void fallsBackToFilenameAndPhysicalPagesIncludingBlankPages(@TempDir Path tempDir) throws Exception {
        ParsedBook book = createPlugin().parse(validSampleFile(tempDir));

        assertThat(book.title()).isEqualTo("course.notes");
        assertThat(book.author()).isEqualTo("Unknown");
        assertThat(book.format()).isEqualTo("pdf");
        assertThat(book.extraMetadata()).containsEntry("pageCount", "3").containsEntry("parserPluginId", "pdf-pdfbox");
        assertThat(book.chapters()).containsExactly(
                new Chapter(0, "第 1 页", "page:1", "Introduction"),
                new Chapter(1, "第 2 页", "page:2", ""),
                new Chapter(2, "第 3 页", "page:3", "Closing notes"));
    }

    @Test
    void bookmarksGroupAdjacentPagesAndKeepFrontMatter(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("outline.pdf");
        try (PDDocument document = new PDDocument()) {
            document.getDocumentInformation().setTitle("开源课程");
            document.getDocumentInformation().setAuthor("课程组");
            document.getDocumentCatalog().setLanguage("zh-CN");
            addPage(document, "Preface");
            PDPage first = addPage(document, "Chapter opening");
            addPage(document, "Chapter continuation");
            PDPage last = addPage(document, "Exercise");

            PDDocumentOutline outline = new PDDocumentOutline();
            document.getDocumentCatalog().setDocumentOutline(outline);
            PDOutlineItem missing = new PDOutlineItem();
            missing.setTitle("失效书签");
            missing.setDestination(new PDNamedDestination("missing-page"));
            outline.addLast(missing);
            PDOutlineItem section = bookmark("第一章", first);
            outline.addLast(section);
            section.addLast(bookmark("同页书签", first));
            section.addLast(bookmark("练习", last));
            document.save(file.toFile());
        }

        ParsedBook book = createPlugin().parse(file);
        assertThat(book.title()).isEqualTo("开源课程");
        assertThat(book.author()).isEqualTo("课程组");
        assertThat(book.language()).isEqualTo("zh-CN");
        assertThat(book.chapters()).containsExactly(
                new Chapter(0, "第 1 页", "page:1", "Preface"),
                new Chapter(1, "第一章", "page:2", "Chapter opening\n\nChapter continuation"),
                new Chapter(2, "练习", "page:4", "Exercise"));
    }

    private static PDOutlineItem bookmark(String title, PDPage page) {
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(title);
        item.setDestination(page);
        return item;
    }

    private static PDPage addPage(PDDocument document, String text) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            stream.newLineAtOffset(72, 700);
            stream.showText(text);
            stream.endText();
        }
        return page;
    }
}

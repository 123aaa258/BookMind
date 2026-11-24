package io.bookmind.plugin.pdf;

import io.bookmind.spi.BookFormatPlugin;
import io.bookmind.spi.Chapter;
import io.bookmind.spi.ParsedBook;
import io.bookmind.spi.PluginErrorCode;
import io.bookmind.spi.PluginException;
import io.bookmind.spi.PluginMetadata;
import io.bookmind.spi.SpiVersion;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parses selectable text into outline sections or individual pages with physical page locators.
 */
public class PdfBookFormatPlugin implements BookFormatPlugin {

    public static final String PLUGIN_ID = "pdf-pdfbox";

    private static final PluginMetadata METADATA = new PluginMetadata(
            PLUGIN_ID,
            "PDF Format Plugin (Apache PDFBox)",
            "0.1.0",
            SpiVersion.CURRENT_API_VERSION,
            "Parses selectable-text PDF textbooks and papers by outline/page for reading and RAG.",
            "BookMind Role 4"
    );

    @Override
    public PluginMetadata metadata() {
        return METADATA;
    }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of("pdf");
    }

    @Override
    public ParsedBook parse(Path filePath) throws PluginException {
        if (filePath == null || !Files.isRegularFile(filePath) || !Files.isReadable(filePath)) {
            throw new PluginException(PluginErrorCode.IO_ERROR, "Cannot read PDF file: " + filePath);
        }
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            if (document.isEncrypted()) {
                throw new PluginException(PluginErrorCode.ENCRYPTED_DOCUMENT, "PDF is encrypted");
            }
            List<String> pages = readPages(document);
            if (pages.stream().allMatch(String::isBlank)) {
                throw new PluginException(PluginErrorCode.EMPTY_CONTENT, "PDF contains no selectable text");
            }
            PDDocumentInformation info = document.getDocumentInformation();
            String title = info.getTitle();
            if (title == null || title.isBlank()) {
                String filename = filePath.getFileName().toString();
                int dot = filename.lastIndexOf('.');
                title = dot > 0 ? filename.substring(0, dot) : filename;
                if (title.isBlank()) {
                    title = filename;
                }
            }
            return new ParsedBook(title.strip(), info.getAuthor(), document.getDocumentCatalog().getLanguage(),
                    "pdf", Optional.empty(), chapters(pages, bookmarkTitles(document)),
                    Map.of("pageCount", Integer.toString(pages.size()), "parserPluginId", PLUGIN_ID));
        } catch (InvalidPasswordException ex) {
            throw new PluginException(PluginErrorCode.ENCRYPTED_DOCUMENT, "PDF requires a password", ex);
        } catch (IOException ex) {
            throw new PluginException(PluginErrorCode.CORRUPTED_FILE, "Could not parse PDF: " + filePath, ex);
        }
    }

    private static List<String> readPages(PDDocument document) throws IOException {
        // PDFTextStripper skips pages without content streams. Reserve their physical page numbers.
        List<String> pages = new ArrayList<>(Collections.nCopies(document.getNumberOfPages(), ""));
        StringWriter pageText = new StringWriter();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void endPage(PDPage page) throws IOException {
                super.endPage(page);
                pages.set(getCurrentPageNo() - 1, pageText.toString().strip());
                pageText.getBuffer().setLength(0);
            }
        };
        stripper.setLineSeparator("\n");
        stripper.writeText(document, pageText);
        return pages;
    }

    private static Map<Integer, String> bookmarkTitles(PDDocument document) {
        Map<Integer, String> titles = new HashMap<>();
        PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
        if (outline == null || outline.getFirstChild() == null) {
            return titles;
        }
        Map<COSDictionary, Integer> pageNumbers = new HashMap<>();
        int number = 1;
        for (PDPage page : document.getPages()) {
            pageNumbers.put(page.getCOSObject(), number++);
        }
        var pending = new ArrayDeque<PDOutlineItem>();
        var visited = new HashSet<COSDictionary>();
        pending.push(outline.getFirstChild());
        while (!pending.isEmpty()) {
            PDOutlineItem item = pending.pop();
            if (!visited.add(item.getCOSObject())) {
                continue;
            }
            if (item.getNextSibling() != null) {
                pending.push(item.getNextSibling());
            }
            if (item.getFirstChild() != null) {
                pending.push(item.getFirstChild());
            }
            try {
                Integer start = bookmarkPage(document, item, pageNumbers);
                String title = item.getTitle();
                if (start != null && title != null && !title.isBlank()) {
                    titles.putIfAbsent(start, title.strip());
                }
            } catch (IOException ex) {
                // An unreadable bookmark destination does not invalidate the page text.
            }
        }
        return titles;
    }

    private static Integer bookmarkPage(PDDocument document, PDOutlineItem item,
                                        Map<COSDictionary, Integer> pageNumbers) throws IOException {
        PDDestination destination = item.getDestination();
        if (destination == null && item.getAction() instanceof PDActionGoTo goTo) {
            destination = goTo.getDestination();
        }
        if (destination instanceof PDNamedDestination named) {
            destination = document.getDocumentCatalog().findNamedDestinationPage(named);
        }
        if (!(destination instanceof PDPageDestination target)) {
            return null;
        }
        if (target.getPage() != null) {
            return pageNumbers.get(target.getPage().getCOSObject());
        }
        int index = target.getPageNumber();
        return index >= 0 && index < document.getNumberOfPages() ? index + 1 : null;
    }

    private static List<Chapter> chapters(List<String> pages, Map<Integer, String> titles) {
        List<Chapter> result = new ArrayList<>();
        int start = 0;
        for (int end = 1; end <= pages.size(); end++) {
            if (end == pages.size() || titles.isEmpty() || titles.containsKey(end + 1)) {
                int page = start + 1;
                result.add(new Chapter(result.size(), titles.getOrDefault(page, "第 " + page + " 页"),
                        "page:" + page, String.join("\n\n", pages.subList(start, end)).strip()));
                start = end;
            }
        }
        return result;
    }
}

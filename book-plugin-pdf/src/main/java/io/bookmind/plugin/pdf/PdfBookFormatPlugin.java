package io.bookmind.plugin.pdf;

import io.bookmind.spi.BookFormatPlugin;
import io.bookmind.spi.ParsedBook;
import io.bookmind.spi.PluginErrorCode;
import io.bookmind.spi.PluginException;
import io.bookmind.spi.PluginMetadata;
import io.bookmind.spi.SpiVersion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Independent PDF format plugin for BookMind (owned exclusively by Role 4).
 *
 * <p>Bootstrap scaffold: Role 4 implements full Apache PDFBox parsing (metadata, outline/page
 * extraction, optional first-page cover rendering, and encrypted/corrupted/empty PDF error
 * mapping) in PR-P1 and PR-P2 without touching {@code book-server} or {@code book-rag}.
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
        if (filePath == null || !Files.exists(filePath)) {
            throw new PluginException(PluginErrorCode.IO_ERROR, "PDF file does not exist: " + filePath);
        }
        throw new PluginException(
                PluginErrorCode.PLUGIN_EXECUTION_FAILED,
                "PDFBox parser implementation pending Role 4 PR-P1."
        );
    }
}

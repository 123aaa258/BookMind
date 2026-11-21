package io.bookmind.tck;

import io.bookmind.spi.BookFormatPlugin;
import io.bookmind.spi.Chapter;
import io.bookmind.spi.ParsedBook;
import io.bookmind.spi.PluginErrorCode;
import io.bookmind.spi.PluginException;
import io.bookmind.spi.PluginMetadata;
import io.bookmind.spi.SpiVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reusable Technology Compatibility Kit (TCK) for every {@link BookFormatPlugin}.
 *
 * <p>Both the built-in TXT parser and independent external plugins (such as {@code book-plugin-pdf})
 * extend this test suite to prove compliance with the BookMind SPI contract.
 */
public abstract class AbstractBookFormatPluginContractTest {

    protected abstract BookFormatPlugin createPlugin();

    protected abstract Path validSampleFile(Path tempDir) throws Exception;

    protected abstract Path corruptedSampleFile(Path tempDir) throws Exception;

    @Test
    void metadataAndExtensionsMustBeValid() {
        BookFormatPlugin plugin = createPlugin();
        PluginMetadata meta = plugin.metadata();

        assertThat(meta).isNotNull();
        assertThat(meta.id()).isNotBlank();
        assertThat(meta.name()).isNotBlank();
        assertThat(meta.version()).isNotBlank();
        assertThat(SpiVersion.isCompatibleWithHost(meta.spiApiVersion(), SpiVersion.CURRENT_API_VERSION))
                .as("Plugin SPI API version %s must be compatible with host %s",
                        meta.spiApiVersion(), SpiVersion.CURRENT_API_VERSION)
                .isTrue();

        assertThat(plugin.supportedExtensions())
                .isNotEmpty()
                .allSatisfy(ext -> {
                    assertThat(ext).isNotBlank();
                    assertThat(ext).doesNotStartWith(".");
                    assertThat(ext).isEqualTo(ext.toLowerCase());
                });
    }

    @Test
    void parsesValidSampleIntoNonEmptyStructuredBook(@TempDir Path tempDir) throws Exception {
        BookFormatPlugin plugin = createPlugin();
        Path sample = validSampleFile(tempDir);

        assertThat(plugin.supports(sample)).isTrue();

        ParsedBook book = plugin.parse(sample);
        assertThat(book).isNotNull();
        assertThat(book.title()).isNotBlank();
        assertThat(book.format()).isIn(plugin.supportedExtensions());

        List<Chapter> chapters = book.chapters();
        assertThat(chapters).isNotEmpty();
        for (int i = 0; i < chapters.size(); i++) {
            Chapter ch = chapters.get(i);
            assertThat(ch.index()).isEqualTo(i);
            assertThat(ch.title()).isNotBlank();
            assertThat(ch.locator()).isNotBlank();
        }
    }

    @Test
    void rejectsMissingFileWithContractIoError(@TempDir Path tempDir) {
        BookFormatPlugin plugin = createPlugin();
        Path missing = tempDir.resolve("does-not-exist." + plugin.supportedExtensions().iterator().next());

        assertThatThrownBy(() -> plugin.parse(missing))
                .isInstanceOf(PluginException.class)
                .extracting(ex -> ((PluginException) ex).errorCode())
                .isEqualTo(PluginErrorCode.IO_ERROR);
    }

    @Test
    void rejectsCorruptedFileWithContractException(@TempDir Path tempDir) throws Exception {
        BookFormatPlugin plugin = createPlugin();
        Path corrupted = corruptedSampleFile(tempDir);

        assertThatThrownBy(() -> plugin.parse(corrupted))
                .isInstanceOf(PluginException.class)
                .extracting(ex -> ((PluginException) ex).errorCode())
                .isIn(PluginErrorCode.CORRUPTED_FILE, PluginErrorCode.EMPTY_CONTENT,
                        PluginErrorCode.UNSUPPORTED_FORMAT);
    }
}

package io.bookmind.spi;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Public Service Provider Interface (SPI) for BookMind e-book format plugins.
 *
 * <p>External plugins MUST implement this interface, register their implementation in
 * {@code META-INF/services/io.bookmind.spi.BookFormatPlugin}, and depend ONLY on
 * {@code io.bookmind:book-spi} (never on {@code book-server} or {@code book-rag}).
 *
 * <p>Compatibility policy: new optional capabilities will be added as {@code default}
 * methods within the same major {@link SpiVersion#CURRENT_API_VERSION}.
 */
public interface BookFormatPlugin {

    /**
     * Returns immutable plugin metadata including plugin ID, version, and declared SPI API version.
     *
     * @return non-null plugin metadata
     */
    PluginMetadata metadata();

    /**
     * Returns lowercase file extensions (without leading dot) handled by this plugin,
     * for example {@code Set.of("pdf")} or {@code Set.of("txt")}.
     *
     * @return non-empty set of lowercase extensions
     */
    Set<String> supportedExtensions();

    /**
     * Checks whether this plugin can parse the given file path.
     * Default implementation performs case-insensitive extension matching.
     *
     * @param filePath candidate book file path
     * @return {@code true} if this plugin should handle the file
     */
    default boolean supports(Path filePath) {
        if (filePath == null || filePath.getFileName() == null) {
            return false;
        }
        String name = filePath.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return supportedExtensions().contains(ext);
    }

    /**
     * Parses a local book file into a unified {@link ParsedBook} representation
     * consumed by both the reader shelf and the RAG indexing pipeline.
     *
     * <p>Implementations MUST wrap format, corruption, encryption, or I/O errors into
     * {@link PluginException} with an accurate {@link PluginErrorCode}.
     *
     * @param filePath path to an existing local book file
     * @return non-null parsed book with at least one chapter
     * @throws PluginException if the file cannot be parsed according to contract
     */
    ParsedBook parse(Path filePath) throws PluginException;
}

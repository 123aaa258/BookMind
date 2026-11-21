package io.bookmind.spi;

import java.util.Objects;

/**
 * Immutable metadata describing a {@link BookFormatPlugin}.
 *
 * @param id            unique kebab-case identifier (e.g. {@code "builtin-txt"}, {@code "pdf-pdfbox"})
 * @param name          human-readable display name
 * @param version       plugin semantic version (e.g. {@code "0.1.0"})
 * @param spiApiVersion target SPI API version (e.g. {@code "1.0"})
 * @param description   short summary of supported capabilities and limitations
 * @param author        plugin author or team name
 */
public record PluginMetadata(
        String id,
        String name,
        String version,
        String spiApiVersion,
        String description,
        String author
) {
    public PluginMetadata {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(spiApiVersion, "spiApiVersion must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        description = description == null ? "" : description;
        author = author == null ? "" : author;
    }
}

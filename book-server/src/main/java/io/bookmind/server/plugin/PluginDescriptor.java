package io.bookmind.server.plugin;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable runtime snapshot describing a built-in or external format plugin.
 *
 * @param id            unique plugin identifier (or JAR filename when discovery failed)
 * @param name          human-readable plugin name
 * @param version       plugin version string
 * @param spiApiVersion declared target SPI API version
 * @param description   short summary of plugin capabilities
 * @param author        plugin author
 * @param builtin       {@code true} for built-in parsers, {@code false} for external JAR plugins
 * @param status        current lifecycle status
 * @param extensions    lowercase supported file extensions
 * @param source        {@code "builtin"} or external JAR filename
 * @param reason        diagnostic detail when status is not {@link PluginStatus#ACTIVE}
 */
public record PluginDescriptor(
        String id,
        String name,
        String version,
        String spiApiVersion,
        String description,
        String author,
        boolean builtin,
        PluginStatus status,
        Set<String> extensions,
        String source,
        String reason
) {
    public PluginDescriptor {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(status, "status must not be null");
        version = version == null ? "" : version;
        spiApiVersion = spiApiVersion == null ? "" : spiApiVersion;
        description = description == null ? "" : description;
        author = author == null ? "" : author;
        extensions = extensions == null ? Set.of() : Set.copyOf(extensions);
        source = source == null ? "" : source;
        reason = reason == null ? "" : reason;
    }
}

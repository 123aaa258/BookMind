package io.bookmind.server.plugin;

import io.bookmind.server.config.BookMindProperties;
import io.bookmind.spi.BookFormatPlugin;
import io.bookmind.spi.ParsedBook;
import io.bookmind.spi.PluginErrorCode;
import io.bookmind.spi.PluginException;
import io.bookmind.spi.PluginMetadata;
import io.bookmind.spi.SpiVersion;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Host runtime responsible for discovering, validating, isolating, and invoking
 * built-in and external {@link BookFormatPlugin} implementations.
 */
@Component
public class PluginManager implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginManager.class);

    private final BookMindProperties properties;
    private final List<BookFormatPlugin> builtinPlugins;
    private final PluginExecutionSandbox sandbox;

    private final List<PluginDescriptor> descriptors = new ArrayList<>();
    private final Map<String, BookFormatPlugin> activePlugins = new LinkedHashMap<>();
    private final List<PluginClassLoader> externalLoaders = new ArrayList<>();

    public PluginManager(BookMindProperties properties, List<BookFormatPlugin> builtinPlugins) {
        this.properties = properties;
        this.builtinPlugins = builtinPlugins == null ? List.of() : List.copyOf(builtinPlugins);
        this.sandbox = new PluginExecutionSandbox(properties.getPluginTimeoutMs());
    }

    @PostConstruct
    public synchronized void reload() {
        closeExternalLoaders();
        descriptors.clear();
        activePlugins.clear();

        Set<String> disabledIds = normalizedDisabledIds();
        for (BookFormatPlugin builtin : builtinPlugins) {
            registerCandidate(builtin, true, "builtin", disabledIds, null);
        }

        loadExternalPlugins(disabledIds);
    }

    public synchronized List<PluginDescriptor> listPlugins() {
        return List.copyOf(descriptors);
    }

    public synchronized Optional<BookFormatPlugin> findActivePluginFor(Path filePath) {
        if (filePath == null) {
            return Optional.empty();
        }
        for (BookFormatPlugin plugin : activePlugins.values()) {
            if (safeSupports(plugin, filePath)) {
                return Optional.of(plugin);
            }
        }
        return Optional.empty();
    }

    public ParsedBook parse(Path filePath) throws PluginException {
        if (filePath == null) {
            throw new PluginException(PluginErrorCode.IO_ERROR, "Book file path must not be null");
        }
        Optional<BookFormatPlugin> matched = findActivePluginFor(filePath);
        if (matched.isPresent()) {
            return sandbox.execute(matched.get(), filePath);
        }

        String ext = extractExtension(filePath);
        if (!ext.isEmpty()) {
            Optional<PluginDescriptor> disabledMatch = findDisabledDescriptorForExtension(ext);
            if (disabledMatch.isPresent()) {
                PluginDescriptor d = disabledMatch.get();
                throw new PluginException(
                        PluginErrorCode.PLUGIN_DISABLED,
                        "Plugin '" + d.id() + "' handling '." + ext + "' is disabled: " + d.reason()
                );
            }
        }

        throw new PluginException(
                PluginErrorCode.UNSUPPORTED_FORMAT,
                "No active plugin supports file: " + filePath.getFileName()
        );
    }

    @PreDestroy
    @Override
    public synchronized void close() {
        closeExternalLoaders();
        sandbox.close();
    }

    private void loadExternalPlugins(Set<String> disabledIds) {
        Path pluginsDir = Path.of(properties.getPluginsDir());
        if (!Files.exists(pluginsDir) || !Files.isDirectory(pluginsDir)) {
            LOGGER.info("External plugins directory does not exist yet: {}", pluginsDir.toAbsolutePath());
            return;
        }

        List<Path> jarPaths;
        try (Stream<Path> stream = Files.list(pluginsDir)) {
            jarPaths = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException ex) {
            LOGGER.warn("Failed to scan external plugins directory {}: {}", pluginsDir, ex.getMessage());
            return;
        }

        for (Path jarPath : jarPaths) {
            loadExternalJar(jarPath, disabledIds);
        }
    }

    private void loadExternalJar(Path jarPath, Set<String> disabledIds) {
        String jarName = jarPath.getFileName().toString();
        try (ZipFile archive = new ZipFile(jarPath.toFile())) {
            if (archive.size() == 0) {
                descriptors.add(failedDescriptor(jarName, jarName, "External plugin JAR is empty: " + jarName));
                return;
            }
        } catch (IOException ex) {
            descriptors.add(failedDescriptor(
                    jarName, jarName, "Corrupted or unreadable plugin JAR " + jarName + ": " + ex.getMessage()
            ));
            return;
        }

        PluginClassLoader loader = null;
        boolean keepLoaderOpen = false;
        try {
            URL jarUrl = jarPath.toUri().toURL();
            loader = new PluginClassLoader(jarUrl, BookFormatPlugin.class.getClassLoader());
            ServiceLoader<BookFormatPlugin> serviceLoader = ServiceLoader.load(BookFormatPlugin.class, loader);
            Iterator<ServiceLoader.Provider<BookFormatPlugin>> iterator = serviceLoader.stream().iterator();

            boolean discoveredAny = false;
            while (iterator.hasNext()) {
                discoveredAny = true;
                try {
                    BookFormatPlugin plugin = iterator.next().get();
                    boolean activated = registerCandidate(plugin, false, jarName, disabledIds, loader);
                    if (activated) {
                        keepLoaderOpen = true;
                    }
                } catch (Throwable ex) {
                    if (ex instanceof VirtualMachineError vmError) {
                        throw vmError;
                    }
                    descriptors.add(failedDescriptor(
                            jarName, jarName, "Failed to instantiate plugin from " + jarName + ": " + ex.getMessage()
                    ));
                }
            }

            if (!discoveredAny) {
                descriptors.add(failedDescriptor(
                        jarName, jarName,
                        "No META-INF/services/" + BookFormatPlugin.class.getName() + " provider found in " + jarName
                ));
            }
        } catch (Throwable ex) {
            if (ex instanceof VirtualMachineError vmError) {
                throw vmError;
            }
            descriptors.add(failedDescriptor(
                    jarName, jarName, "Failed to load external plugin JAR " + jarName + ": " + ex.getMessage()
            ));
        } finally {
            if (loader != null) {
                if (keepLoaderOpen) {
                    externalLoaders.add(loader);
                } else {
                    closeQuietly(loader);
                }
            }
        }
    }

    private boolean registerCandidate(
            BookFormatPlugin plugin,
            boolean builtin,
            String source,
            Set<String> disabledIds,
            PluginClassLoader loader
    ) {
        PluginMetadata meta;
        Set<String> extensions;
        try {
            meta = plugin.metadata();
            extensions = normalizeExtensions(plugin.supportedExtensions());
            if (meta == null || extensions.isEmpty()) {
                descriptors.add(failedDescriptor(
                        source, source, "Plugin returned null metadata or empty supportedExtensions"
                ));
                return false;
            }
        } catch (Throwable ex) {
            if (ex instanceof VirtualMachineError vmError) {
                throw vmError;
            }
            descriptors.add(failedDescriptor(
                    source, source, "Plugin metadata inspection failed: " + ex.getMessage()
            ));
            return false;
        }

        if (disabledIds.contains(meta.id().toLowerCase(Locale.ROOT))) {
            descriptors.add(new PluginDescriptor(
                    meta.id(), meta.name(), meta.version(), meta.spiApiVersion(),
                    meta.description(), meta.author(), builtin, PluginStatus.DISABLED,
                    extensions, source, "Disabled via bookmind.disabled-plugins configuration"
            ));
            return false;
        }

        if (!isSpiCompatible(meta.spiApiVersion())) {
            descriptors.add(new PluginDescriptor(
                    meta.id(), meta.name(), meta.version(), meta.spiApiVersion(),
                    meta.description(), meta.author(), builtin, PluginStatus.INCOMPATIBLE,
                    extensions, source,
                    "Plugin SPI API version '" + meta.spiApiVersion()
                            + "' is incompatible with host SPI API version '"
                            + SpiVersion.CURRENT_API_VERSION + "'"
            ));
            return false;
        }

        if (activePlugins.containsKey(meta.id())) {
            descriptors.add(new PluginDescriptor(
                    meta.id(), meta.name(), meta.version(), meta.spiApiVersion(),
                    meta.description(), meta.author(), builtin, PluginStatus.FAILED,
                    extensions, source, "Duplicate plugin ID already registered: " + meta.id()
            ));
            return false;
        }

        activePlugins.put(meta.id(), plugin);
        descriptors.add(new PluginDescriptor(
                meta.id(), meta.name(), meta.version(), meta.spiApiVersion(),
                meta.description(), meta.author(), builtin, PluginStatus.ACTIVE,
                extensions, source, ""
        ));
        LOGGER.info("Activated {} plugin '{}' v{} (SPI {}) from {} for extensions {}",
                builtin ? "built-in" : "external", meta.id(), meta.version(),
                meta.spiApiVersion(), source, extensions);
        return loader != null;
    }

    private synchronized Optional<PluginDescriptor> findDisabledDescriptorForExtension(String ext) {
        return descriptors.stream()
                .filter(d -> d.status() == PluginStatus.DISABLED)
                .filter(d -> d.extensions().contains(ext))
                .findFirst();
    }

    private Set<String> normalizedDisabledIds() {
        Set<String> result = new HashSet<>();
        for (String id : properties.getDisabledPlugins()) {
            if (id != null && !id.isBlank()) {
                result.add(id.trim().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static Set<String> normalizeExtensions(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        for (String ext : raw) {
            if (ext != null && !ext.isBlank()) {
                String cleaned = ext.trim().toLowerCase(Locale.ROOT);
                if (cleaned.startsWith(".")) {
                    cleaned = cleaned.substring(1);
                }
                if (!cleaned.isEmpty()) {
                    normalized.add(cleaned);
                }
            }
        }
        return Set.copyOf(normalized);
    }

    private static boolean isSpiCompatible(String pluginApiVersion) {
        try {
            return SpiVersion.isCompatibleWithHost(pluginApiVersion, SpiVersion.CURRENT_API_VERSION);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean safeSupports(BookFormatPlugin plugin, Path filePath) {
        try {
            return plugin.supports(filePath);
        } catch (Throwable ex) {
            if (ex instanceof VirtualMachineError vmError) {
                throw vmError;
            }
            LOGGER.warn("Plugin supports() threw exception for {}: {}", filePath, ex.getMessage());
            return false;
        }
    }

    private static String extractExtension(Path filePath) {
        Path fileName = filePath.getFileName();
        if (fileName == null) {
            return "";
        }
        String name = fileName.toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static PluginDescriptor failedDescriptor(String id, String source, String reason) {
        return new PluginDescriptor(
                id, id, "", "", "", "", false, PluginStatus.FAILED, Set.of(), source, reason
        );
    }

    private void closeExternalLoaders() {
        for (PluginClassLoader loader : externalLoaders) {
            closeQuietly(loader);
        }
        externalLoaders.clear();
    }

    private static void closeQuietly(PluginClassLoader loader) {
        try {
            loader.close();
        } catch (IOException ex) {
            LOGGER.warn("Failed to close PluginClassLoader: {}", ex.getMessage());
        }
    }
}

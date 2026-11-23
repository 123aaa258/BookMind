package io.bookmind.server.plugin;

import io.bookmind.server.config.BookMindProperties;
import io.bookmind.spi.BookFormatPlugin;
import io.bookmind.spi.Chapter;
import io.bookmind.spi.ParsedBook;
import io.bookmind.spi.PluginErrorCode;
import io.bookmind.spi.PluginException;
import io.bookmind.spi.PluginMetadata;
import io.bookmind.spi.SpiVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginManagerTest {

    @Test
    void loadsValidExternalPluginJarWithChildFirstIsolation(@TempDir Path tempDir) throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);

        String helperSrc = """
                package ext.mock;
                public final class PrivateHelper {
                    public static String loaderType() {
                        return PrivateHelper.class.getClassLoader().getClass().getName();
                    }
                }
                """;
        String pluginSrc = """
                package ext.mock;
                import io.bookmind.spi.BookFormatPlugin;
                import io.bookmind.spi.Chapter;
                import io.bookmind.spi.ParsedBook;
                import io.bookmind.spi.PluginMetadata;
                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.util.List;
                import java.util.Map;
                import java.util.Optional;
                import java.util.Set;

                public class MockExternalPlugin implements BookFormatPlugin {
                    @Override
                    public PluginMetadata metadata() {
                        return new PluginMetadata("ext-mock", "Mock External", "0.1.0", "1.0", "demo", "test");
                    }
                    @Override
                    public Set<String> supportedExtensions() {
                        return Set.of("mock");
                    }
                    @Override
                    public ParsedBook parse(Path filePath) {
                        try {
                            String text = Files.readString(filePath);
                            return new ParsedBook(
                                    "Mock Book", "Tester", "zh-CN", "mock",
                                    Optional.empty(),
                                    List.of(new Chapter(0, "Page 1", "page:1", text)),
                                    Map.of("loader", PrivateHelper.loaderType())
                            );
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                """;

        Path builtJar = PluginTestJarBuilder.buildPluginJar(
                tempDir.resolve("build"),
                "ext-mock-plugin.jar",
                "ext.mock.MockExternalPlugin",
                Map.of(
                        "ext/mock/PrivateHelper.java", helperSrc,
                        "ext/mock/MockExternalPlugin.java", pluginSrc
                )
        );
        Files.copy(builtJar, pluginsDir.resolve("ext-mock-plugin.jar"));

        Path bookFile = tempDir.resolve("demo.mock");
        Files.writeString(bookFile, "External plugin parsed content", StandardCharsets.UTF_8);

        BookMindProperties props = new BookMindProperties();
        props.setPluginsDir(pluginsDir.toString());

        try (PluginManager manager = new PluginManager(props, List.of())) {
            manager.reload();

            assertThat(manager.listPlugins())
                    .hasSize(1)
                    .first()
                    .satisfies(desc -> {
                        assertThat(desc.id()).isEqualTo("ext-mock");
                        assertThat(desc.builtin()).isFalse();
                        assertThat(desc.status()).isEqualTo(PluginStatus.ACTIVE);
                        assertThat(desc.source()).isEqualTo("ext-mock-plugin.jar");
                        assertThat(desc.extensions()).containsExactly("mock");
                    });

            ParsedBook parsed = manager.parse(bookFile);
            assertThat(parsed.title()).isEqualTo("Mock Book");
            assertThat(parsed.chapters().get(0).content()).isEqualTo("External plugin parsed content");
            assertThat(parsed.extraMetadata().get("loader")).isEqualTo(PluginClassLoader.class.getName());
        }
    }

    @Test
    void disablesPluginViaConfigAndReportsPluginDisabledError(@TempDir Path tempDir) throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);

        Path builtJar = buildSimplePluginJar(tempDir.resolve("build"), "ext-disabled.jar",
                "ext.dis.DisabledExtPlugin", "ext-disabled", "1.0", "dis");
        Files.copy(builtJar, pluginsDir.resolve("ext-disabled.jar"));

        BookMindProperties props = new BookMindProperties();
        props.setPluginsDir(pluginsDir.toString());
        props.setDisabledPlugins(List.of("ext-disabled"));

        try (PluginManager manager = new PluginManager(props, List.of())) {
            manager.reload();

            assertThat(manager.listPlugins())
                    .hasSize(1)
                    .first()
                    .extracting(PluginDescriptor::status)
                    .isEqualTo(PluginStatus.DISABLED);

            assertThatThrownBy(() -> manager.parse(tempDir.resolve("sample.dis")))
                    .isInstanceOf(PluginException.class)
                    .extracting(ex -> ((PluginException) ex).errorCode())
                    .isEqualTo(PluginErrorCode.PLUGIN_DISABLED);
        }
    }

    @Test
    void rejectsIncompatibleApiVersionAndCorruptJarWithoutBreakingValidPlugins(@TempDir Path tempDir)
            throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);

        Path incompatibleJar = buildSimplePluginJar(tempDir.resolve("build1"), "future-v2.jar",
                "ext.v2.FuturePlugin", "future-v2", "2.0", "fut");
        Files.copy(incompatibleJar, pluginsDir.resolve("future-v2.jar"));
        Files.writeString(pluginsDir.resolve("corrupt.jar"), "not-a-valid-zip-jar", StandardCharsets.UTF_8);

        BookFormatPlugin healthyBuiltin = new StubPlugin("builtin-ok", "ok", file -> new ParsedBook(
                "Healthy", "Author", "en", "ok", Optional.empty(),
                List.of(new Chapter(0, "Ch1", "chapter:1", "ok body")), Map.of()
        ));

        BookMindProperties props = new BookMindProperties();
        props.setPluginsDir(pluginsDir.toString());

        try (PluginManager manager = new PluginManager(props, List.of(healthyBuiltin))) {
            manager.reload();

            assertThat(manager.listPlugins())
                    .extracting(PluginDescriptor::id, PluginDescriptor::status)
                    .containsExactlyInAnyOrder(
                            org.assertj.core.groups.Tuple.tuple("builtin-ok", PluginStatus.ACTIVE),
                            org.assertj.core.groups.Tuple.tuple("future-v2", PluginStatus.INCOMPATIBLE),
                            org.assertj.core.groups.Tuple.tuple("corrupt.jar", PluginStatus.FAILED)
                    );

            ParsedBook parsed = manager.parse(tempDir.resolve("good.ok"));
            assertThat(parsed.title()).isEqualTo("Healthy");
        }
    }

    @Test
    void sandboxContainsRuntimeExceptionLinkageErrorNullReturnAndTimeout(@TempDir Path tempDir) throws Exception {
        BookFormatPlugin runtimeCrash = new StubPlugin("fault-runtime", "rt", file -> {
            throw new IllegalStateException("boom");
        });
        BookFormatPlugin linkageCrash = new StubPlugin("fault-linkage", "lnk", file -> {
            throw new NoClassDefFoundError("missing/Dep");
        });
        BookFormatPlugin nullReturn = new StubPlugin("fault-null", "nil", file -> null);
        BookFormatPlugin slowPlugin = new StubPlugin("fault-slow", "slow", file -> {
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return null;
        });

        BookMindProperties props = new BookMindProperties();
        props.setPluginsDir(tempDir.resolve("empty-plugins").toString());
        props.setPluginTimeoutMs(150L);

        try (PluginManager manager = new PluginManager(
                props, List.of(runtimeCrash, linkageCrash, nullReturn, slowPlugin))) {
            manager.reload();

            assertThatThrownBy(() -> manager.parse(tempDir.resolve("a.rt")))
                    .isInstanceOf(PluginException.class)
                    .extracting(ex -> ((PluginException) ex).errorCode())
                    .isEqualTo(PluginErrorCode.PLUGIN_EXECUTION_FAILED);

            assertThatThrownBy(() -> manager.parse(tempDir.resolve("a.lnk")))
                    .isInstanceOf(PluginException.class)
                    .extracting(ex -> ((PluginException) ex).errorCode())
                    .isEqualTo(PluginErrorCode.PLUGIN_EXECUTION_FAILED);

            assertThatThrownBy(() -> manager.parse(tempDir.resolve("a.nil")))
                    .isInstanceOf(PluginException.class)
                    .extracting(ex -> ((PluginException) ex).errorCode())
                    .isEqualTo(PluginErrorCode.PLUGIN_EXECUTION_FAILED);

            assertThatThrownBy(() -> manager.parse(tempDir.resolve("a.slow")))
                    .isInstanceOf(PluginException.class)
                    .extracting(ex -> ((PluginException) ex).errorCode())
                    .isEqualTo(PluginErrorCode.PLUGIN_TIMEOUT);
        }
    }

    private static Path buildSimplePluginJar(
            Path workDir,
            String jarName,
            String fqcn,
            String pluginId,
            String apiVersion,
            String ext
    ) throws Exception {
        int lastDot = fqcn.lastIndexOf('.');
        String pkg = fqcn.substring(0, lastDot);
        String cls = fqcn.substring(lastDot + 1);
        String source = """
                package %s;
                import io.bookmind.spi.BookFormatPlugin;
                import io.bookmind.spi.Chapter;
                import io.bookmind.spi.ParsedBook;
                import io.bookmind.spi.PluginMetadata;
                import java.nio.file.Path;
                import java.util.List;
                import java.util.Map;
                import java.util.Optional;
                import java.util.Set;
                public class %s implements BookFormatPlugin {
                    @Override
                    public PluginMetadata metadata() {
                        return new PluginMetadata("%s", "%s", "0.1.0", "%s", "desc", "test");
                    }
                    @Override
                    public Set<String> supportedExtensions() {
                        return Set.of("%s");
                    }
                    @Override
                    public ParsedBook parse(Path filePath) {
                        return new ParsedBook("T", "A", "en", "%s", Optional.empty(),
                                List.of(new Chapter(0, "C1", "page:1", "body")), Map.of());
                    }
                }
                """.formatted(pkg, cls, pluginId, pluginId, apiVersion, ext, ext);
        return PluginTestJarBuilder.buildPluginJar(
                workDir, jarName, fqcn, Map.of(fqcn.replace('.', '/') + ".java", source)
        );
    }

    @FunctionalInterface
    private interface ParseFn {
        ParsedBook apply(Path path) throws PluginException;
    }

    private static final class StubPlugin implements BookFormatPlugin {
        private final String id;
        private final String ext;
        private final ParseFn fn;

        private StubPlugin(String id, String ext, ParseFn fn) {
            this.id = id;
            this.ext = ext;
            this.fn = fn;
        }

        @Override
        public PluginMetadata metadata() {
            return new PluginMetadata(id, id, "0.1.0", SpiVersion.CURRENT_API_VERSION, "stub", "test");
        }

        @Override
        public Set<String> supportedExtensions() {
            return Set.of(ext);
        }

        @Override
        public ParsedBook parse(Path filePath) throws PluginException {
            return fn.apply(filePath);
        }
    }
}

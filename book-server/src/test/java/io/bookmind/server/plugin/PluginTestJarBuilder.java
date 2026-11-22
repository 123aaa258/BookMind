package io.bookmind.server.plugin;

import io.bookmind.spi.BookFormatPlugin;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * Test utility that dynamically compiles temporary external plugin classes and packages them
 * into real drop-in JARs with {@code META-INF/services/io.bookmind.spi.BookFormatPlugin}.
 */
final class PluginTestJarBuilder {

    private PluginTestJarBuilder() {
    }

    static Path buildPluginJar(
            Path workDir,
            String jarFileName,
            String providerFqcn,
            Map<String, String> javaSources
    ) throws IOException {
        Path srcDir = workDir.resolve("src-" + jarFileName.replace(".jar", ""));
        Path classesDir = workDir.resolve("classes-" + jarFileName.replace(".jar", ""));
        Files.createDirectories(srcDir);
        Files.createDirectories(classesDir);

        List<Path> sourceFiles = new ArrayList<>();
        for (Map.Entry<String, String> entry : javaSources.entrySet()) {
            Path file = srcDir.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.getValue(), StandardCharsets.UTF_8);
            sourceFiles.add(file);
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK JavaCompiler is required to run dynamic plugin JAR tests");
        }

        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            var compilationUnits = fm.getJavaFileObjectsFromPaths(sourceFiles);
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classesDir.toString()
            );
            Boolean success = compiler.getTask(null, fm, null, options, null, compilationUnits).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IllegalStateException("Failed to compile test plugin sources for " + jarFileName);
            }
        }

        Path jarPath = workDir.resolve(jarFileName);
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
            try (Stream<Path> walk = Files.walk(classesDir)) {
                for (Path classFile : walk.filter(Files::isRegularFile).toList()) {
                    String entryName = classesDir.relativize(classFile).toString().replace('\\', '/');
                    jos.putNextEntry(new JarEntry(entryName));
                    jos.write(Files.readAllBytes(classFile));
                    jos.closeEntry();
                }
            }

            if (providerFqcn != null && !providerFqcn.isBlank()) {
                String servicePath = "META-INF/services/" + BookFormatPlugin.class.getName();
                jos.putNextEntry(new JarEntry(servicePath));
                jos.write((providerFqcn + "\n").getBytes(StandardCharsets.UTF_8));
                jos.closeEntry();
            }
        }
        return jarPath;
    }
}

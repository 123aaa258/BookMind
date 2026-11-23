package io.bookmind.server.plugin;

import io.bookmind.spi.BookFormatPlugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.List;

/**
 * Child-first isolated {@link URLClassLoader} for external BookMind plugin JARs.
 *
 * <p>Inspired by PF4J's {@code PluginClassLoader}:
 * <ul>
 *   <li>JDK packages and {@code io.bookmind.spi.*} are always loaded parent-first via the
 *       Host's SPI ClassLoader so plugin and host share identical contract types inside
 *       Spring Boot's executable JAR environment.</li>
 *   <li>Plugin-private dependencies (e.g. Apache PDFBox bundled in a shaded plugin JAR)
 *       are resolved child-first from the plugin JAR itself.</li>
 *   <li>SPI service descriptors ({@code META-INF/services/io.bookmind.spi.BookFormatPlugin})
 *       are resolved strictly from this plugin JAR to prevent parent classpath leakage.</li>
 * </ul>
 */
public class PluginClassLoader extends URLClassLoader {

    private static final String SPI_SERVICE_RESOURCE =
            "META-INF/services/" + BookFormatPlugin.class.getName();

    private static final List<String> PARENT_FIRST_PREFIXES = List.of(
            "java.",
            "javax.",
            "jdk.",
            "sun.",
            "org.xml.",
            "org.w3c.",
            "org.ietf.",
            "io.bookmind.spi.",
            "org.slf4j."
    );

    public PluginClassLoader(URL pluginJarUrl, ClassLoader spiParentClassLoader) {
        super(new URL[] { pluginJarUrl }, spiParentClassLoader);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) {
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }

            if (isParentFirst(name)) {
                return super.loadClass(name, resolve);
            }

            try {
                Class<?> childClass = findClass(name);
                if (resolve) {
                    resolveClass(childClass);
                }
                return childClass;
            } catch (ClassNotFoundException ignored) {
                return super.loadClass(name, resolve);
            }
        }
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (SPI_SERVICE_RESOURCE.equals(name)) {
            return findResources(name);
        }
        return super.getResources(name);
    }

    private static boolean isParentFirst(String className) {
        for (String prefix : PARENT_FIRST_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}

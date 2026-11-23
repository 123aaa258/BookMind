package io.bookmind.server.plugin;

import io.bookmind.spi.BookFormatPlugin;
import io.bookmind.spi.ParsedBook;
import io.bookmind.spi.PluginErrorCode;
import io.bookmind.spi.PluginException;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fault-containment boundary around {@link BookFormatPlugin#parse(Path)}.
 *
 * <p>Enforces per-invocation timeout, switches the thread context ClassLoader to the plugin's
 * ClassLoader during parsing, and converts unexpected {@link Throwable}s or {@code null} results
 * into contract {@link PluginException}s without crashing the host server.
 */
public class PluginExecutionSandbox implements AutoCloseable {

    private final long timeoutMs;
    private final ExecutorService executor;

    public PluginExecutionSandbox(long timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be > 0");
        }
        this.timeoutMs = timeoutMs;
        AtomicInteger counter = new AtomicInteger(1);
        this.executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "bookmind-plugin-exec-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    public ParsedBook execute(BookFormatPlugin plugin, Path filePath) throws PluginException {
        Objects.requireNonNull(plugin, "plugin must not be null");
        Objects.requireNonNull(filePath, "filePath must not be null");
        String pluginId = safePluginId(plugin);

        Future<ParsedBook> future = executor.submit(() -> invokeWithPluginTccl(plugin, filePath));
        try {
            ParsedBook result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (result == null) {
                throw new PluginException(
                        PluginErrorCode.PLUGIN_EXECUTION_FAILED,
                        "Plugin '" + pluginId + "' returned null ParsedBook for file: " + filePath.getFileName()
                );
            }
            return result;
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new PluginException(
                    PluginErrorCode.PLUGIN_TIMEOUT,
                    "Plugin '" + pluginId + "' timed out after " + timeoutMs + "ms parsing: "
                            + filePath.getFileName(),
                    ex
            );
        } catch (InterruptedException ex) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new PluginException(
                    PluginErrorCode.PLUGIN_EXECUTION_FAILED,
                    "Parsing interrupted while invoking plugin '" + pluginId + "'",
                    ex
            );
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof PluginException pluginEx) {
                throw pluginEx;
            }
            if (cause instanceof VirtualMachineError vmError) {
                throw vmError;
            }
            throw new PluginException(
                    PluginErrorCode.PLUGIN_EXECUTION_FAILED,
                    "Plugin '" + pluginId + "' failed unexpectedly: "
                            + (cause == null ? ex.getMessage() : cause.getClass().getSimpleName()
                            + ": " + cause.getMessage()),
                    cause == null ? ex : cause
            );
        }
    }

    private ParsedBook invokeWithPluginTccl(BookFormatPlugin plugin, Path filePath) throws PluginException {
        Thread current = Thread.currentThread();
        ClassLoader originalTccl = current.getContextClassLoader();
        current.setContextClassLoader(plugin.getClass().getClassLoader());
        try {
            return plugin.parse(filePath);
        } finally {
            current.setContextClassLoader(originalTccl);
        }
    }

    private static String safePluginId(BookFormatPlugin plugin) {
        try {
            return plugin.metadata().id();
        } catch (Throwable ignored) {
            return plugin.getClass().getName();
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}

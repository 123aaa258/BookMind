package io.bookmind.server.plugin;

/**
 * Lifecycle state of a discovered built-in or external {@code BookFormatPlugin}.
 */
public enum PluginStatus {
    ACTIVE,
    DISABLED,
    INCOMPATIBLE,
    FAILED
}

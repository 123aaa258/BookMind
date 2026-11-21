package io.bookmind.spi;

/**
 * Standardized error codes shared across Host and all format plugins.
 */
public enum PluginErrorCode {
    UNSUPPORTED_FORMAT,
    CORRUPTED_FILE,
    ENCRYPTED_DOCUMENT,
    EMPTY_CONTENT,
    IO_ERROR,
    INCOMPATIBLE_API_VERSION,
    PLUGIN_DISABLED,
    PLUGIN_TIMEOUT,
    PLUGIN_EXECUTION_FAILED
}

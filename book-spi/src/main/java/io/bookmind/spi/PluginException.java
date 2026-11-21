package io.bookmind.spi;

import java.util.Objects;

/**
 * Checked contract exception thrown by format plugins or the host isolation layer.
 */
public class PluginException extends Exception {

    private final PluginErrorCode errorCode;

    public PluginException(PluginErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public PluginException(PluginErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public PluginErrorCode errorCode() {
        return errorCode;
    }
}

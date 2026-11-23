package io.bookmind.server.library;

/**
 * Diagnostic record for a library file that could not be parsed during scanning.
 */
public record ScanFailure(String file, String errorCode, String message) {
}

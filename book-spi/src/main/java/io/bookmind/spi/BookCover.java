package io.bookmind.spi;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable book cover image payload returned by a format plugin.
 *
 * @param mimeType standard image MIME type (e.g. {@code "image/png"} or {@code "image/jpeg"})
 * @param bytes    non-empty raw image bytes (defensively copied)
 */
public record BookCover(String mimeType, byte[] bytes) {

    public BookCover {
        Objects.requireNonNull(mimeType, "mimeType must not be null");
        Objects.requireNonNull(bytes, "bytes must not be null");
        if (mimeType.isBlank()) {
            throw new IllegalArgumentException("mimeType must not be blank");
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("bytes must not be empty");
        }
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}

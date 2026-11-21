package io.bookmind.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Unified immutable book representation produced by {@link BookFormatPlugin#parse(java.nio.file.Path)}.
 *
 * @param title         non-blank display title
 * @param author        author name (defaults to {@code "Unknown"} when blank)
 * @param language      BCP-47 or short language code (defaults to {@code "und"})
 * @param format        lowercase format name (e.g. {@code "txt"}, {@code "pdf"})
 * @param cover         optional cover image
 * @param chapters      non-empty ordered list of chapters/pages
 * @param extraMetadata optional format-specific attributes (e.g. {@code pageCount}, {@code producer})
 */
public record ParsedBook(
        String title,
        String author,
        String language,
        String format,
        Optional<BookCover> cover,
        List<Chapter> chapters,
        Map<String, String> extraMetadata
) {
    public ParsedBook {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(chapters, "chapters must not be null");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (format.isBlank()) {
            throw new IllegalArgumentException("format must not be blank");
        }
        if (chapters.isEmpty()) {
            throw new IllegalArgumentException("chapters must not be empty");
        }
        author = (author == null || author.isBlank()) ? "Unknown" : author;
        language = (language == null || language.isBlank()) ? "und" : language;
        cover = cover == null ? Optional.empty() : cover;
        chapters = List.copyOf(chapters);
        extraMetadata = extraMetadata == null ? Map.of() : Map.copyOf(extraMetadata);
    }
}

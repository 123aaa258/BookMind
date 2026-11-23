package io.bookmind.server.library;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.bookmind.spi.BookCover;
import io.bookmind.spi.Chapter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Lightweight local file persistence under {@code bookmind.data-dir}.
 *
 * <p>Stores catalog metadata atomically in {@code library-index.json} and per-book chapters/covers
 * under {@code books/<bookId>/}.
 */
final class LibraryStorage {

    private static final TypeReference<List<Chapter>> CHAPTER_LIST_TYPE = new TypeReference<>() {
    };

    private final Path dataDir;
    private final Path indexFile;
    private final Path booksDir;
    private final ObjectMapper objectMapper;

    LibraryStorage(Path dataDir, ObjectMapper objectMapper) {
        this.dataDir = dataDir;
        this.indexFile = dataDir.resolve("library-index.json");
        this.booksDir = dataDir.resolve("books");
        this.objectMapper = objectMapper;
    }

    LibraryIndexSnapshot loadIndex() {
        if (!Files.exists(indexFile)) {
            return LibraryIndexSnapshot.empty();
        }
        try {
            return objectMapper.readValue(indexFile.toFile(), LibraryIndexSnapshot.class);
        } catch (IOException ex) {
            return LibraryIndexSnapshot.empty();
        }
    }

    void saveIndex(LibraryIndexSnapshot snapshot) {
        try {
            Files.createDirectories(dataDir);
            Path tmp = dataDir.resolve("library-index.json.tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), snapshot);
            Files.move(tmp, indexFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to persist library-index.json", ex);
        }
    }

    boolean hasChapters(String bookId) {
        return Files.exists(bookDir(bookId).resolve("chapters.json"));
    }

    void saveChapters(String bookId, List<Chapter> chapters) {
        try {
            Path dir = bookDir(bookId);
            Files.createDirectories(dir);
            Path tmp = dir.resolve("chapters.json.tmp");
            objectMapper.writeValue(tmp.toFile(), chapters);
            Files.move(tmp, dir.resolve("chapters.json"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to persist chapters for book " + bookId, ex);
        }
    }

    List<Chapter> loadChapters(String bookId) {
        Path file = bookDir(bookId).resolve("chapters.json");
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(file.toFile(), CHAPTER_LIST_TYPE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read chapters for book " + bookId, ex);
        }
    }

    void saveCover(String bookId, Optional<BookCover> cover) {
        Path coverPath = bookDir(bookId).resolve("cover.bin");
        try {
            if (cover.isEmpty()) {
                Files.deleteIfExists(coverPath);
                return;
            }
            Files.createDirectories(bookDir(bookId));
            Files.write(coverPath, cover.get().bytes());
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to persist cover for book " + bookId, ex);
        }
    }

    Optional<byte[]> loadCoverBytes(String bookId) {
        Path coverPath = bookDir(bookId).resolve("cover.bin");
        if (!Files.exists(coverPath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(coverPath));
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read cover for book " + bookId, ex);
        }
    }

    void deleteBookAssets(String bookId) {
        Path dir = bookDir(bookId);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to delete cached assets for book " + bookId, ex);
        }
    }

    private Path bookDir(String bookId) {
        return booksDir.resolve(bookId);
    }
}

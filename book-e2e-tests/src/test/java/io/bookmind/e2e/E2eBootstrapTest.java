package io.bookmind.e2e;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bootstrap black-box E2E test suite (owned by Role 4).
 *
 * <p>Role 4 expands this suite in PR-P3 to load the compiled {@code book-plugin-pdf.jar}
 * from an external plugins directory against the packaged host without modifying core code.
 */
class E2eBootstrapTest {

    @Test
    void sampleBookDirectoryExistsInRepository() {
        Path sampleTxt = Path.of("..", "samples", "books", "sample-oss-guide.txt");
        assertThat(Files.exists(sampleTxt))
                .as("Default TXT sample book should exist at %s", sampleTxt.toAbsolutePath())
                .isTrue();
    }
}

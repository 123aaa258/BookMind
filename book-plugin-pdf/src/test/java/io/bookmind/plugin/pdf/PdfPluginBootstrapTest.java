package io.bookmind.plugin.pdf;

import io.bookmind.spi.BookFormatPlugin;
import io.bookmind.spi.SpiVersion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class PdfPluginBootstrapTest {

    @Test
    void pluginIsDiscoverableViaJavaServiceLoader() {
        List<BookFormatPlugin> loaded = ServiceLoader.load(BookFormatPlugin.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        assertThat(loaded)
                .hasSize(1)
                .first()
                .satisfies(plugin -> {
                    assertThat(plugin.metadata().id()).isEqualTo(PdfBookFormatPlugin.PLUGIN_ID);
                    assertThat(plugin.metadata().spiApiVersion()).isEqualTo(SpiVersion.CURRENT_API_VERSION);
                    assertThat(plugin.supportedExtensions()).containsExactly("pdf");
                });
    }
}

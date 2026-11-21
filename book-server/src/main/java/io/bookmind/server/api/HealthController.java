package io.bookmind.server.api;

import io.bookmind.server.config.BookMindProperties;
import io.bookmind.spi.SpiVersion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final BookMindProperties properties;

    public HealthController(BookMindProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        boolean llmConfigured = properties.getRag().getApiKey() != null
                && !properties.getRag().getApiKey().isBlank();
        String llmModel = properties.getRag().getModel() == null ? "" : properties.getRag().getModel();
        return Map.of(
                "status", "UP",
                "service", "BookMind",
                "spiApiVersion", SpiVersion.CURRENT_API_VERSION,
                "llmConfigured", llmConfigured,
                "llmModel", llmModel
        );
    }
}

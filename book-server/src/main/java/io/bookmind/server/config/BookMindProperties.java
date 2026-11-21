package io.bookmind.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Strongly typed runtime configuration for BookMind.
 *
 * <p>Sensitive credentials (such as {@code rag.apiKey}) MUST be supplied via environment
 * variables (e.g. {@code BOOKMIND_LLM_API_KEY}) and NEVER committed into version control.
 */
@ConfigurationProperties(prefix = "bookmind")
public class BookMindProperties {

    private String libraryDir = "./samples/books";
    private String dataDir = "./data";
    private String pluginsDir = "./plugins";
    private long pluginTimeoutMs = 15000L;
    private List<String> disabledPlugins = new ArrayList<>();
    private RagProperties rag = new RagProperties();

    public String getLibraryDir() {
        return libraryDir;
    }

    public void setLibraryDir(String libraryDir) {
        this.libraryDir = libraryDir;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getPluginsDir() {
        return pluginsDir;
    }

    public void setPluginsDir(String pluginsDir) {
        this.pluginsDir = pluginsDir;
    }

    public long getPluginTimeoutMs() {
        return pluginTimeoutMs;
    }

    public void setPluginTimeoutMs(long pluginTimeoutMs) {
        this.pluginTimeoutMs = pluginTimeoutMs;
    }

    public List<String> getDisabledPlugins() {
        return disabledPlugins;
    }

    public void setDisabledPlugins(List<String> disabledPlugins) {
        this.disabledPlugins = disabledPlugins == null ? new ArrayList<>() : new ArrayList<>(disabledPlugins);
    }

    public RagProperties getRag() {
        return rag;
    }

    public void setRag(RagProperties rag) {
        this.rag = rag == null ? new RagProperties() : rag;
    }

    public static class RagProperties {
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey = "";
        private String model = "gpt-4o-mini";
        private int timeoutSeconds = 30;
        private int topK = 5;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }
    }
}

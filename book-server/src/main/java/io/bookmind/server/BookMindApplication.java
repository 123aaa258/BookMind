package io.bookmind.server;

import io.bookmind.rag.BootstrapRagService;
import io.bookmind.rag.RagService;
import io.bookmind.server.config.BookMindProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(BookMindProperties.class)
public class BookMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookMindApplication.class, args);
    }

    @Bean
    @ConditionalOnMissingBean(RagService.class)
    public RagService fallbackBootstrapRagService() {
        return new BootstrapRagService();
    }
}

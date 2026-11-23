package io.bookmind.server.api;

import io.bookmind.rag.RagErrorCode;
import io.bookmind.rag.RagException;
import io.bookmind.rag.RagService;
import io.bookmind.rag.RagStreamListener;
import io.bookmind.rag.model.Citation;
import io.bookmind.rag.model.RagAnswer;
import io.bookmind.rag.model.RagAskRequest;
import io.bookmind.rag.model.SearchHit;
import io.bookmind.spi.ParsedBook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RagStreamControllerTest {

    @TempDir
    static Path tempRoot;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("bookmind.library-dir", () -> tempRoot.resolve("books").toString());
        registry.add("bookmind.data-dir", () -> tempRoot.resolve("data").toString());
        registry.add("bookmind.plugins-dir", () -> tempRoot.resolve("plugins").toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void streamsCitationsDeltasAndDoneEventsOverSse() throws Exception {
        String payload = """
                {
                  "question": "什么是插件隔离？",
                  "topK": 3,
                  "history": [
                    {"role": "user", "content": "介绍一下 BookMind"},
                    {"role": "assistant", "content": "BookMind 是可扩展电子书库 [1]。"}
                  ]
                }
                """;

        MvcResult started = mockMvc.perform(post("/api/rag/ask/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completed = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(completed.getResponse().getContentType()).contains(MediaType.TEXT_EVENT_STREAM_VALUE);
        String body = completed.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("event:citations");
        assertThat(body).contains("chapter:1");
        assertThat(body).contains("event:delta");
        assertThat(body).contains("通过独立类加载器实现");
        assertThat(body).contains("event:done");
        assertThat(body.indexOf("event:citations")).isLessThan(body.indexOf("event:delta"));
        assertThat(body.indexOf("event:delta")).isLessThan(body.indexOf("event:done"));
    }

    @Test
    void emitsMidStreamSseErrorEventWhenUpstreamFailsAfterCitations() throws Exception {
        MvcResult started = mockMvc.perform(post("/api/rag/ask/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"TRIGGER_MID_ERROR\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completed = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn();

        String body = completed.getResponse().getContentAsString();
        assertThat(body).contains("event:citations");
        assertThat(body).contains("event:error");
        assertThat(body).contains("LLM_TIMEOUT");
    }

    @Test
    void supportsSyncAskWithMultiTurnHistory() throws Exception {
        mockMvc.perform(post("/api/rag/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"什么是插件隔离？\",\"history\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("通过独立类加载器实现 [1]。"))
                .andExpect(jsonPath("$.citations[0].locator").value("chapter:1"));
    }

    @TestConfiguration
    static class StubRagConfig {

        @Bean
        @Primary
        RagService stubStreamingRagService() {
            Citation sampleCitation = new Citation(
                    1, "book-1", "开源导论", 0, "第一章", "chapter:1", "核心契约与插件隔离", 3.2f
            );
            return new RagService() {
                @Override
                public void indexBook(String bookId, ParsedBook book) {
                }

                @Override
                public void removeBook(String bookId) {
                }

                @Override
                public List<SearchHit> search(String query, Optional<String> optionalBookId, int topK) {
                    return List.of();
                }

                @Override
                public RagAnswer ask(RagAskRequest request) {
                    return new RagAnswer(
                            request.question(), "通过独立类加载器实现 [1]。", "gpt-4o-mini", List.of(sampleCitation)
                    );
                }

                @Override
                public void askStream(RagAskRequest request, RagStreamListener listener) {
                    listener.onCitations(List.of(sampleCitation));
                    if ("TRIGGER_MID_ERROR".equals(request.question())) {
                        listener.onError(new RagException(RagErrorCode.LLM_TIMEOUT, "Upstream timed out"));
                        return;
                    }
                    listener.onDelta("通过独立类加载器实现 ");
                    listener.onDelta("[1]。");
                    listener.onComplete("gpt-4o-mini");
                }
            };
        }
    }
}

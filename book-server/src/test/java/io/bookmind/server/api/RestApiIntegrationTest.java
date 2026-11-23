package io.bookmind.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RestApiIntegrationTest {

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

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exercisesFullBookLibraryPluginAndPreStreamErrorEndpoints() throws Exception {
        // 1. Plugins endpoint exposes active built-in TXT parser
        mockMvc.perform(get("/api/plugins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("builtin-txt"))
                .andExpect(jsonPath("$[0].builtin").value(true))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        // 2. Upload valid TXT book via multipart
        MockMultipartFile validTxt = new MockMultipartFile(
                "file",
                "demo-guide.txt",
                "text/plain",
                "《分布式系统导论》\n作者：王五\n\n第一章 共识算法\nRaft 共识机制说明。".getBytes(StandardCharsets.UTF_8)
        );
        MvcResult uploadResult = mockMvc.perform(multipart("/api/books/upload").file(validTxt))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("分布式系统导论"))
                .andExpect(jsonPath("$.author").value("王五"))
                .andExpect(jsonPath("$.format").value("txt"))
                .andExpect(jsonPath("$.chapterCount").value(1))
                .andReturn();

        JsonNode created = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        String bookId = created.get("id").asText();
        assertThat(bookId).isNotBlank();

        // 3. List books with format & keyword filters, fetch detail, chapters, chapter content, cover 404
        mockMvc.perform(get("/api/books").param("format", "txt").param("q", "分布式"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(bookId));

        mockMvc.perform(get("/api/books/{id}", bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("分布式系统导论"));

        mockMvc.perform(get("/api/books/{id}/chapters", bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].locator").value("chapter:1"));

        mockMvc.perform(get("/api/books/{id}/chapters/0", bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Raft 共识机制说明。"));

        mockMvc.perform(get("/api/books/{id}/cover", bookId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("COVER_NOT_FOUND"));

        // 4. Uploading unsupported PDF without plugin returns 422 ApiErrorResponse
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "sample.pdf", "application/pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/books/upload").file(pdfFile))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FORMAT"));

        // 5. Library status & scan endpoints
        mockMvc.perform(get("/api/library/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookCount").value(1));

        mockMvc.perform(post("/api/library/scan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scannedCount").value(1));

        // 6. RAG search & pre-stream LLM_NOT_CONFIGURED returns JSON 503 even on SSE route
        mockMvc.perform(get("/api/rag/search").param("q", "Raft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(post("/api/rag/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"什么是 Raft？\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("LLM_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.details.requiredEnvVar").value("BOOKMIND_LLM_API_KEY"));

        mockMvc.perform(post("/api/rag/ask/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"什么是 Raft？\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("LLM_NOT_CONFIGURED"));

        // 7. Delete book & confirm 404 afterwards
        mockMvc.perform(delete("/api/books/{id}", bookId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/books/{id}", bookId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
    }
}

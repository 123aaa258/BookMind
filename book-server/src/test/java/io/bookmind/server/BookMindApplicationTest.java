package io.bookmind.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookMindApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReturnsSpiVersionAndModelMetadataWithoutSecrets() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("BookMind"))
                .andExpect(jsonPath("$.spiApiVersion").value("1.0"))
                .andExpect(jsonPath("$.llmModel").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.apiKey").doesNotExist());
    }
}

package com.agentx.infra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModelConfigApiTest {

    @Autowired MockMvc mvc;

    private static String body() {
        return """
                {"name":"deepseek-chat-%d","providerType":"DEEPSEEK","baseUrl":null,
                 "apiKey":"sk-test","modelName":"deepseek-chat","type":"CHAT","enabled":true}
                """.formatted(System.nanoTime());
    }

    @Test
    void adminCanCreateAndKeyIsMasked() throws Exception {
        mvc.perform(post("/api/v1/admin/model-configs")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(APPLICATION_JSON).content(body()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maskedApiKey").value("sk-****"));
    }

    @Test
    void normalUserForbidden() throws Exception {
        mvc.perform(post("/api/v1/admin/model-configs")
                        .with(user("bob").roles("USER"))
                        .contentType(APPLICATION_JSON).content(body()))
                .andExpect(status().isForbidden());
    }
}

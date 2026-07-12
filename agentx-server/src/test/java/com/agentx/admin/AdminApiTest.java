package com.agentx.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiTest {

    @Autowired MockMvc mvc;

    @Test
    void adminCanCreateUserAndSeeStats() throws Exception {
        mvc.perform(post("/api/v1/admin/users")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"user-%d\",\"password\":\"pass1234\",\"nickname\":\"测试\",\"role\":\"USER\"}"
                                .formatted(System.nanoTime())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"));

        mvc.perform(get("/api/v1/admin/stats/tokens/summary").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_calls").exists());

        mvc.perform(get("/api/v1/admin/stats/tokens/daily").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void normalUserCannotAccessAdmin() throws Exception {
        mvc.perform(get("/api/v1/admin/users").with(user("bob").roles("USER")))
                .andExpect(status().isForbidden());
    }
}

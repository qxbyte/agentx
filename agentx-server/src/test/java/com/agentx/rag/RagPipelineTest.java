package com.agentx.rag;

import com.agentx.auth.domain.SysUserRepository;
import com.agentx.auth.security.AuthPrincipal;
import com.agentx.infra.ai.client.EmbeddingModelFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RAG 摄取全链路（真 PG + 固定向量 embedding stub）：
 * 建库 → 上传 md → 任务轮询至 SUCCEEDED → 分段落库 → 命中测试返回结果。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RagPipelineTest {

    @Autowired MockMvc mvc;
    @Autowired SysUserRepository userRepository;
    @MockitoBean EmbeddingModelFactory embeddingModelFactory;

    /** 固定向量模型：所有文本 → 同一单位向量（cosine 相似度恒 1，命中必回）。 */
    static class FixedEmbeddingModel implements EmbeddingModel {
        private static float[] unit() {
            float[] v = new float[1024];
            v[0] = 1.0f;
            return v;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return new EmbeddingResponse(IntStream.range(0, request.getInstructions().size())
                    .mapToObj(i -> new Embedding(unit(), i))
                    .toList());
        }

        @Override
        public float[] embed(Document document) {
            return unit();
        }
    }

    @Test
    void uploadIngestAndHitTest() throws Exception {
        when(embeddingModelFactory.getDefault()).thenReturn(new FixedEmbeddingModel());
        when(embeddingModelFactory.get(any())).thenReturn(new FixedEmbeddingModel());

        var admin = userRepository.findByUsername("admin").orElseThrow();
        var auth = new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(admin.getId(), admin.getUsername(), admin.getRole()),
                null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        String kbBody = mvc.perform(post("/api/v1/kb").with(authentication(auth))
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"测试知识库\",\"chunkSize\":128,\"chunkOverlap\":16}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String kbId = extract(kbBody, "\"id\":\"");

        String content = "AgentX 是企业级智能体平台。".repeat(30);
        String docBody = mvc.perform(multipart("/api/v1/kb/" + kbId + "/documents")
                        .file(new MockMultipartFile("file", "intro.md", "text/markdown",
                                content.getBytes()))
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String docId = extract(docBody, "\"id\":\"");

        // 轮询任务直至完成（虚拟线程后台执行）
        String taskStatus = "";
        for (int i = 0; i < 60; i++) {
            Thread.sleep(200);
            String task = mvc.perform(get("/api/v1/kb/documents/" + docId + "/task")
                            .with(authentication(auth)))
                    .andReturn().getResponse().getContentAsString();
            if (task.contains("SUCCEEDED") || task.contains("FAILED")) {
                taskStatus = task;
                break;
            }
        }
        assertThat(taskStatus).contains("SUCCEEDED");

        mvc.perform(get("/api/v1/kb/documents/" + docId + "/segments").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThan(1)));

        mvc.perform(post("/api/v1/kb/" + kbId + "/hit-test").with(authentication(auth))
                        .contentType(APPLICATION_JSON)
                        .content("{\"query\":\"什么是 AgentX\",\"topK\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data[0].docName").value("intro.md"));
    }

    private static String extract(String json, String marker) {
        int idx = json.indexOf(marker) + marker.length();
        return json.substring(idx, idx + 36);
    }
}

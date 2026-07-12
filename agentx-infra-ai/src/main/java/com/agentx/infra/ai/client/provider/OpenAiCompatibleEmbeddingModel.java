package com.agentx.infra.ai.client.provider;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 /embeddings 端点的最小 EmbeddingModel 实现（通义/智谱/vLLM 等）。
 * <p>
 * 自实现而非引入 spring-ai-openai：后者 2.0 绑定 openai-java SDK（独立 OkHttp 栈），
 * 而 embeddings 协议极简（单端点单形态），~60 行 RestClient 即覆盖，
 * 保持平台 HTTP 技术栈统一（与 ChatModel 侧的 DeepSeek 协议客户端一致）。
 */
public class OpenAiCompatibleEmbeddingModel implements EmbeddingModel {

    private final RestClient restClient;
    private final String modelName;

    public OpenAiCompatibleEmbeddingModel(String baseUrl, String apiKey, String modelName) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.modelName = modelName;
    }

    /** OpenAI embeddings 协议的响应形态。 */
    record ApiResponse(List<Item> data) {
        record Item(float[] embedding, int index) {}
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        ApiResponse response = restClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", modelName, "input", request.getInstructions()))
                .retrieve()
                .body(ApiResponse.class);
        if (response == null || response.data() == null) {
            throw new IllegalStateException("embedding 端点返回空响应");
        }
        List<Embedding> embeddings = response.data().stream()
                .map(item -> new Embedding(item.embedding(), item.index()))
                .toList();
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }
}

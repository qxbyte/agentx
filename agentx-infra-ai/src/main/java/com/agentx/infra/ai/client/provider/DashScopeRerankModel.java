package com.agentx.infra.ai.client.provider;

import com.agentx.infra.ai.client.RerankModel;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

/**
 * DashScope 文本重排（gte-rerank）客户端。DashScope 原生 rerank API：
 * <pre>
 * POST {baseUrl}/api/v1/services/rerank/text-rerank/text-rerank
 * body: { model, input:{query, documents}, parameters:{top_n, return_documents:false} }
 * resp: { output:{ results:[{index, relevance_score}] }, usage:{...} }
 * </pre>
 * baseUrl 通常为 https://dashscope.aliyuncs.com。自实现 ~50 行 RestClient，保持平台 HTTP
 * 技术栈统一。指向任何兼容此请求/响应形态的重排服务亦可（换 baseUrl/model 即可）。
 */
public class DashScopeRerankModel implements RerankModel {

    private static final String RERANK_PATH = "/api/v1/services/rerank/text-rerank/text-rerank";

    private final RestClient restClient;
    private final String modelName;

    public DashScopeRerankModel(String baseUrl, String apiKey, String modelName) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.modelName = modelName;
    }

    record ApiResponse(Output output) {
        record Output(List<Result> results) {}
        record Result(int index, double relevance_score) {}
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topN) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        int n = Math.min(Math.max(topN, 1), documents.size());
        ApiResponse resp = restClient.post()
                .uri(RERANK_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", modelName,
                        "input", Map.of("query", query, "documents", documents),
                        "parameters", Map.of("top_n", n, "return_documents", false)))
                .retrieve()
                .body(ApiResponse.class);
        if (resp == null || resp.output() == null || resp.output().results() == null) {
            throw new IllegalStateException("rerank 端点返回空响应");
        }
        return resp.output().results().stream()
                .map(r -> new RerankResult(r.index(), r.relevance_score()))
                .toList();
    }
}

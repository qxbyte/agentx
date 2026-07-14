package com.agentx.rag.retrieval;

import com.agentx.rag.domain.ExternalKb;
import com.agentx.rag.vector.VectorMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 外部知识库检索（方案 B）：只把**查询文本**携 vault 发给各外部库的 search API，
 * 由外部库用它自己的 embedding 模型向量化并检索——AgentX 不为外部检索做向量化，
 * 彻底解耦两侧 embedding 模型。fail-open：单库超时/异常只丢该库结果并记日志，绝不断流。
 * 命中转为 Document 并对齐 rag-source 元数据（doc_name/doc_id/segment_id + 定位字段），
 * 前端引用来源无差别展示（doc_name 前缀外部库名以示来源）。
 * <p>
 * 检索质量（多查询扩展、RRF 融合、rerank）由 AgentX 在 composite 合并层统一施加，
 * 本类只负责"把文本查询打给外部库、取回候选"这一最小职责。
 */
@Slf4j
@Component
public class ExternalKbRetriever {

    /** 用同一段查询文本并行检索所有启用的外部库。 */
    public List<Document> retrieve(String query, List<ExternalKb> kbs) {
        if (kbs.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        if (kbs.size() == 1) {
            return searchOne(kbs.getFirst(), query);
        }
        // 多外部库并行检索：各库一条虚拟线程，避免串行 HTTP 延迟叠加。
        // searchOne 自身 fail-open（异常返回空），单库故障不影响其余。
        List<Document> out = new ArrayList<>();
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<List<Document>>> futures = kbs.stream()
                    .map(kb -> executor.submit(() -> searchOne(kb, query)))
                    .toList();
            for (java.util.concurrent.Future<List<Document>> f : futures) {
                try {
                    out.addAll(f.get());
                } catch (Exception e) {
                    log.warn("外部知识库检索任务异常（已跳过）: {}", e.getMessage());
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Document> searchOne(ExternalKb kb, String query) {
        try {
            Map<String, Object> resp = com.agentx.rag.service.ExternalKbHttp.client(kb.getBaseUrl())
                    .post().uri(kb.getSearchPath())
                    .body(Map.of(
                            "vault", kb.getVaultId(),
                            "query", query,
                            "topK", kb.getTopK(),
                            "threshold", kb.getSimilarityThreshold()))
                    .retrieve().body(Map.class);
            Object hits = resp == null ? null : resp.get("hits");
            if (!(hits instanceof List<?> list)) {
                return List.of();
            }
            List<Document> docs = new ArrayList<>();
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> hit)) continue;
                String text = String.valueOf(hit.get("text"));
                double score = hit.get("score") instanceof Number n ? n.doubleValue() : 0.0;
                String title = hit.get("title") == null ? "片段" : String.valueOf(hit.get("title"));
                String path = hit.get("path") == null ? "" : String.valueOf(hit.get("path"));
                Map<String, Object> meta = new java.util.LinkedHashMap<>();
                meta.put(VectorMetadata.DOC_NAME, kb.getName() + " · " + title);
                meta.put(VectorMetadata.DOC_ID, "external:" + kb.getId());
                meta.put(VectorMetadata.SEGMENT_ID, path);
                // 定位字段（模板可选约定）：有则透传，前端引用来源卡片据此展示出处
                if (!path.isEmpty()) {
                    meta.put(VectorMetadata.DOC_PATH, path);
                }
                if (hit.get("headings") instanceof List<?> hs && !hs.isEmpty()) {
                    meta.put(VectorMetadata.HEADINGS, hs.stream().map(String::valueOf).toList());
                }
                if (hit.get("startLine") instanceof Number sl) {
                    meta.put(VectorMetadata.START_LINE, sl.intValue());
                }
                if (hit.get("endLine") instanceof Number el) {
                    meta.put(VectorMetadata.END_LINE, el.intValue());
                }
                docs.add(Document.builder().text(text).score(score).metadata(meta).build());
            }
            return docs;
        } catch (Exception e) {
            // 单库故障不影响整体检索（停用开关之外的运行时兜底）
            log.warn("外部知识库「{}」检索失败（已跳过）: {}", kb.getName(), e.getMessage());
            return List.of();
        }
    }
}

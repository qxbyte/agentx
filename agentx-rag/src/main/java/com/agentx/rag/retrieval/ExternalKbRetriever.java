package com.agentx.rag.retrieval;

import com.agentx.infra.ai.client.EmbeddingModelFactory;
import com.agentx.rag.domain.ExternalKb;
import com.agentx.rag.vector.VectorMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 外部知识库检索（设计：本地向量模型对 query 向量化一次，携 vault 调各外部库的
 * search API 取相似文本）。fail-open：单库超时/异常只丢该库结果并记日志，绝不断流。
 * 命中转为 Document 并对齐本地 rag-source 元数据（doc_name/doc_id/segment_id），
 * 前端引用来源无差别展示（doc_name 前缀外部库名以示来源）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalKbRetriever {

    private final EmbeddingModelFactory embeddingModelFactory;

    /** 对启用中的外部库逐一检索；query 只向量化一次。 */
    public List<Document> retrieve(String query, List<ExternalKb> kbs) {
        if (kbs.isEmpty()) {
            return List.of();
        }
        float[] vector;
        try {
            vector = embeddingModelFactory.getDefault().embed(query);
        } catch (Exception e) {
            log.warn("外部知识库检索跳过：query 向量化失败 - {}", e.getMessage());
            return List.of();
        }
        List<Float> boxed = new ArrayList<>(vector.length);
        for (float v : vector) boxed.add(v);

        List<Document> out = new ArrayList<>();
        for (ExternalKb kb : kbs) {
            out.addAll(searchOne(kb, boxed));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Document> searchOne(ExternalKb kb, List<Float> vector) {
        try {
            Map<String, Object> resp = com.agentx.rag.service.ExternalKbHttp.client(kb.getBaseUrl())
                    .post().uri(kb.getSearchPath())
                    .body(Map.of(
                            "vault", kb.getVaultId(),
                            "vector", vector,
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

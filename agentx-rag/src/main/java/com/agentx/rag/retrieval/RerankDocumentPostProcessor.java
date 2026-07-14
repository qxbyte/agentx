package com.agentx.rag.retrieval;

import com.agentx.infra.ai.client.RerankModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import java.util.List;

/**
 * rerank 精排后处理器（检索质量层最后一环）：对 RRF 融合出的候选池（本地 + 外部合并）
 * 用 cross-encoder 逐条精算 query×候选相关性，重排并收窄到最终 topK。
 * <p>
 * 位于 RetrievalAugmentationAdvisor 的 documentPostProcessors——即 RRF 融合之后。
 * 精排后最终顺序来自 reranker 的统一打分，异构来源（本地 PGVector / 外部余弦）的原始分
 * 只用于进候选池、不参与最终排序。fail-open：rerank 失败退回 RRF 序（截断到 topK），不断流。
 */
@Slf4j
public class RerankDocumentPostProcessor implements DocumentPostProcessor {

    private final RerankModel rerankModel;
    private final int topK;

    public RerankDocumentPostProcessor(RerankModel rerankModel, int topK) {
        this.rerankModel = rerankModel;
        this.topK = Math.max(1, topK);
    }

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        if (documents == null || documents.size() <= 1) {
            return documents;
        }
        try {
            List<String> texts = documents.stream()
                    .map(d -> d.getText() == null ? "" : d.getText())
                    .toList();
            List<RerankModel.RerankResult> ranked = rerankModel.rerank(query.text(), texts, topK);
            if (ranked.isEmpty()) {
                return documents.stream().limit(topK).toList();
            }
            return ranked.stream()
                    .filter(r -> r.index() >= 0 && r.index() < documents.size())
                    .map(r -> withScore(documents.get(r.index()), r.relevanceScore()))
                    .toList();
        } catch (Exception e) {
            log.warn("rerank 精排失败，退回 RRF 排序：{}", e.getMessage());
            return documents.stream().limit(topK).toList();
        }
    }

    /** 回写 rerank 相关度分作为展示分（精排后最终相关度以 reranker 为准）。 */
    private static Document withScore(Document d, double score) {
        return Document.builder()
                .id(d.getId())
                .text(d.getText())
                .metadata(d.getMetadata())
                .score(score)
                .build();
    }
}

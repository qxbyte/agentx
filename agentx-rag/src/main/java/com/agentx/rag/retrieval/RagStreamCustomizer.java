package com.agentx.rag.retrieval;

import com.agentx.infra.ai.stream.ChatStreamContext;
import com.agentx.infra.ai.stream.ChatStreamCustomizer;
import com.agentx.rag.domain.ExternalKb;
import com.agentx.rag.domain.KnowledgeBase;
import com.agentx.rag.service.ExternalKbService;
import com.agentx.rag.service.KnowledgeBaseService;
import com.agentx.rag.vector.VectorMetadata;
import com.agentx.rag.vector.VectorStoreFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RAG 检索定制（设计文档 §4.7 检索侧）：会话/Agent 绑定知识库时注入
 * RetrievalAugmentationAdvisor。命中文档经 advisor context（DOCUMENT_CONTEXT）
 * 由 chat 层转 rag-source 帧。
 * <p>
 * 检索源融合：本地知识库（kbIds）与启用中的外部知识库（external_kb）共存——
 * 组合成单一 DocumentRetriever，结果按相似度降序合并；外部库停用即完全跳过（解耦）。
 * <p>
 * @Order(20)：在 AgentStreamCustomizer(10) 之后执行，以消费其合并的 kbIds。
 * 多知识库约束：一次检索用第一个库的检索参数（topK/threshold）与 embedding
 * 模型（跨不同 embedding 模型的库混检无意义，属配置错误，此处取首库为准）。
 */
@Order(20)
@Component
@RequiredArgsConstructor
public class RagStreamCustomizer implements ChatStreamCustomizer {

    private final KnowledgeBaseService knowledgeBaseService;
    private final VectorStoreFactory vectorStoreFactory;
    private final ExternalKbService externalKbService;
    private final ExternalKbRetriever externalKbRetriever;

    @Override
    public void customize(ChatStreamContext context, ChatClient.ChatClientRequestSpec spec) {
        List<ExternalKb> externals = externalKbService.listEnabled();
        if (context.kbIds().isEmpty() && externals.isEmpty()) {
            return;
        }

        DocumentRetriever local = context.kbIds().isEmpty() ? null : localRetriever(context);
        DocumentRetriever composite = query -> {
            List<Document> merged = new ArrayList<>();
            if (local != null) {
                merged.addAll(local.retrieve(query));
            }
            merged.addAll(externalKbRetriever.retrieve(query.text(), externals));
            merged.sort((a, b) -> Double.compare(score(b), score(a)));
            return merged;
        };

        spec.advisors(RetrievalAugmentationAdvisor.builder()
                .documentRetriever(composite)
                // 未命中不硬拒答：让模型基于通用知识回答并说明未在知识库命中
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build())
                .build());
    }

    private DocumentRetriever localRetriever(ChatStreamContext context) {
        List<KnowledgeBase> kbs = context.kbIds().stream()
                .map(knowledgeBaseService::getInternal)
                .toList();
        KnowledgeBase primary = kbs.getFirst();
        List<String> kbIdValues = kbs.stream().map(kb -> kb.getId().toString())
                .map(Object::toString).toList();
        return VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStoreFactory.forKb(primary))
                .topK(primary.getTopK())
                .similarityThreshold(primary.getSimilarityThreshold())
                .filterExpression(new FilterExpressionBuilder()
                        .in(VectorMetadata.KB_ID, kbIdValues.toArray())
                        .build())
                .build();
    }

    private static double score(Document d) {
        return d.getScore() == null ? 0.0 : d.getScore();
    }
}

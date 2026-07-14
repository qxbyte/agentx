package com.agentx.rag.retrieval;

import com.agentx.infra.ai.client.ChatClientFactory;
import com.agentx.infra.ai.stream.ChatStreamContext;
import com.agentx.infra.ai.stream.ChatStreamCustomizer;
import com.agentx.rag.domain.ExternalKb;
import com.agentx.rag.domain.KnowledgeBase;
import com.agentx.rag.domain.KnowledgeBaseRepository;
import com.agentx.rag.service.ExternalKbService;
import com.agentx.rag.vector.VectorMetadata;
import com.agentx.rag.vector.VectorStoreFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 检索定制（设计文档 §4.7 检索侧）：会话/Agent 绑定知识库时注入
 * RetrievalAugmentationAdvisor。命中文档经 advisor context（DOCUMENT_CONTEXT）
 * 由 chat 层转 rag-source 帧。
 * <p>
 * 知识库是会话/项目的创建期属性：context.kbIds()（会话固化 + 项目默认合并）为空
 * 即完全不检索——未选择知识库的对话绝不引入召回。kbIds 中的 id 分流：命中
 * external_kb 的走外部检索（且须 enabled，停用即忽略，解耦开关）；其余走本地
 * 向量库。两路结果按相似度降序合并为单一 DocumentRetriever。
 * <p>
 * @Order(20)：在 AgentStreamCustomizer(10) 之后执行，以消费其合并的 kbIds。
 * 多知识库约束：一次检索用第一个库的检索参数（topK/threshold）与 embedding
 * 模型（跨不同 embedding 模型的库混检无意义，属配置错误，此处取首库为准）。
 */
@Slf4j
@Order(20)
@Component
@RequiredArgsConstructor
public class RagStreamCustomizer implements ChatStreamCustomizer {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final VectorStoreFactory vectorStoreFactory;
    private final ExternalKbService externalKbService;
    private final ExternalKbRetriever externalKbRetriever;
    private final ChatClientFactory chatClientFactory;

    @Override
    public void customize(ChatStreamContext context, ChatClient.ChatClientRequestSpec spec) {
        if (context.kbIds().isEmpty()) {
            return; // 未选择知识库的对话绝不引入召回
        }
        // 分流：会话选中的 id 命中外部库（且启用）→ 外部检索；其余按本地库处理
        List<ExternalKb> externals = externalKbService.listEnabled().stream()
                .filter(kb -> context.kbIds().contains(kb.getId()))
                .toList();
        List<KnowledgeBase> localKbs = knowledgeBaseRepository.findAllById(context.kbIds());
        if (externals.isEmpty() && localKbs.isEmpty()) {
            return;
        }

        DocumentRetriever local = localKbs.isEmpty() ? null : localRetriever(localKbs);
        DocumentRetriever composite = query -> {
            List<Document> merged = new ArrayList<>();
            if (local != null) {
                merged.addAll(local.retrieve(query));
            }
            merged.addAll(externalKbRetriever.retrieve(query.text(), externals));
            merged.sort((a, b) -> Double.compare(score(b), score(a)));
            return merged;
        };

        var advisorBuilder = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(composite)
                // 未命中不硬拒答：让模型基于通用知识回答并说明未在知识库命中
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build());
        // 多轮改写：把带指代/省略的追问（"它的性能如何"）改写成自足查询再检索，
        // 提升多轮召回。用默认 CHAT 模型；默认模型缺失时降级为不改写，不阻断检索。
        QueryTransformer rewrite = rewriteTransformer();
        if (rewrite != null) {
            advisorBuilder.queryTransformers(rewrite);
        }
        spec.advisors(advisorBuilder.build());
    }

    /** 构建多轮改写器（默认 CHAT 模型）；不可用时返回 null，检索照常进行。 */
    private QueryTransformer rewriteTransformer() {
        try {
            return RewriteQueryTransformer.builder()
                    .chatClientBuilder(chatClientFactory.getDefault().mutate())
                    .build();
        } catch (Exception e) {
            log.warn("查询改写不可用（默认 CHAT 模型缺失？），本次跳过改写：{}", e.getMessage());
            return null;
        }
    }

    private DocumentRetriever localRetriever(List<KnowledgeBase> kbs) {
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

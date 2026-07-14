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
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
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
 * external_kb 的走外部检索（须 enabled，停用即忽略，解耦开关）；其余走本地向量库。
 * <p>
 * <b>统一检索质量层（本地 + 外部 + 混检同一条管线）：</b>
 * 多轮上下文化改写（RewriteQueryTransformer）→ 多查询扩展（MultiQueryExpander，宽召回）
 * → 每个查询变体经 composite 检索（本地 PGVector + 外部库文本检索合并）→ RRF 跨查询/
 * 跨来源融合（{@link ReciprocalRankFusionJoiner}，按名次收敛，消化异构分数不可比）。
 * 质量由 AgentX 独占把控，不依赖外部库实现混合检索/重排。rerank 精排为后续独立一相。
 * <p>
 * @Order(20)：在 AgentStreamCustomizer(10) 之后执行，以消费其合并的 kbIds。
 * 多本地库约束：用首库的检索参数（topK/threshold）与向量库（跨不同 embedding 模型的
 * 本地库混检无意义，属配置错误，此处取首库为准）。
 */
@Slf4j
@Order(20)
@Component
@RequiredArgsConstructor
public class RagStreamCustomizer implements ChatStreamCustomizer {

    /**
     * 中文查询改写模板（替换 Spring AI 默认英文模板，提升中文追问的改写质量）。
     * 必含 {@code {target}} 与 {@code {query}} 两个占位符——RewriteQueryTransformer
     * 构造时强校验，缺失即抛异常。
     */
    private static final PromptTemplate REWRITE_PROMPT = new PromptTemplate("""
            你是查询改写助手。请把用户的提问改写成更适合在{target}中做语义检索的查询。
            要求：
            1. 补全指代与省略：把"它""这个""上面说的"等替换为上文明确的实体，使查询自足、脱离上下文也能理解。
            2. 去掉寒暄、语气词和与检索无关的表述，保持简洁、聚焦要点。
            3. 保留原始语言（中文提问改写为中文）；只输出改写后的查询本身，不要作答、不要解释。

            原始查询：
            {query}

            改写后的查询：
            """);

    /**
     * 中文多查询扩展模板（替换默认英文模板）。必含 {@code {number}} 与 {@code {query}}
     * 占位符——MultiQueryExpander 构造时强校验，缺失即抛异常。
     */
    private static final PromptTemplate MULTI_QUERY_PROMPT = new PromptTemplate("""
            你是信息检索与查询优化专家。请为给定查询生成 {number} 个不同版本的检索查询。
            要求：
            1. 每个变体从不同角度/侧面切入，但都紧扣原查询的核心意图，以扩大检索覆盖面、提升召回。
            2. 保留原始语言（中文查询生成中文变体）。
            3. 不要解释、不要编号、不要多余文字；每个变体单独一行，用换行分隔。

            原始查询：{query}

            查询变体：
            """);

    /** 多查询扩展生成的变体数（另含原查询，故实际检索约 NUM_QUERIES+1 路）。
     *  3 在召回增益与 LLM/检索成本间较平衡。 */
    private static final int NUM_QUERIES = 3;
    /** 无本地/外部库检索参数可依时的兜底候选池上限。 */
    private static final int DEFAULT_TOP_K = 8;

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

        // composite：单个查询变体的一次检索——本地 PGVector + 各外部库（文本检索）合并，
        // 按分降序给出该查询的候选名次（作为 RRF 的名次输入；跨源分数仅用于组内排序）。
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

        int fusionTopK = fusionTopK(localKbs, externals);
        var advisorBuilder = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(composite)
                // RRF 融合 + 截断到 topK：跨查询/跨来源按名次收敛为最终候选池（单查询时退化为保序+截断）
                .documentJoiner(new ReciprocalRankFusionJoiner(fusionTopK))
                // 未命中不硬拒答：让模型基于通用知识回答并说明未在知识库命中
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build());
        // 多轮改写（上下文化）：把带指代/省略的追问改写成自足查询再检索。
        QueryTransformer rewrite = rewriteTransformer();
        if (rewrite != null) {
            advisorBuilder.queryTransformers(rewrite);
        }
        // 多查询扩展（宽召回）：原查询 + N-1 个不同角度变体各自检索，交给 RRF 融合。
        QueryExpander expander = multiQueryExpander();
        if (expander != null) {
            advisorBuilder.queryExpander(expander);
        }
        spec.advisors(advisorBuilder.build());
    }

    /** 融合候选池上限：优先本地首库 topK，否则外部首库 topK，再否则兜底默认值。 */
    private static int fusionTopK(List<KnowledgeBase> localKbs, List<ExternalKb> externals) {
        if (!localKbs.isEmpty()) {
            return localKbs.getFirst().getTopK();
        }
        if (!externals.isEmpty()) {
            return externals.getFirst().getTopK();
        }
        return DEFAULT_TOP_K;
    }

    /** 多查询扩展器（默认 CHAT 模型，含原查询）；不可用时返回 null，退回单查询检索。 */
    private QueryExpander multiQueryExpander() {
        try {
            return MultiQueryExpander.builder()
                    .chatClientBuilder(chatClientFactory.getDefault().mutate())
                    .promptTemplate(MULTI_QUERY_PROMPT)
                    .includeOriginal(true)
                    .numberOfQueries(NUM_QUERIES)
                    .build();
        } catch (Exception e) {
            log.warn("多查询扩展不可用（默认 CHAT 模型缺失？），退回单查询：{}", e.getMessage());
            return null;
        }
    }

    /** 构建多轮改写器（默认 CHAT 模型）；不可用时返回 null，检索照常进行。 */
    private QueryTransformer rewriteTransformer() {
        try {
            return RewriteQueryTransformer.builder()
                    .chatClientBuilder(chatClientFactory.getDefault().mutate())
                    .promptTemplate(REWRITE_PROMPT)
                    .targetSearchSystem("向量知识库")
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

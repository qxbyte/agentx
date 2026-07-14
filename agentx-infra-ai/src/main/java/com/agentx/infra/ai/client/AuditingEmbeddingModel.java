package com.agentx.infra.ai.client;

import com.agentx.infra.ai.audit.AiCallAuditor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * EmbeddingModel 审计装饰器：拦截每次 {@link #call} 记一条 EMBEDDING 审计（模型名/token/
 * 延迟/状态），与 CHAT 分开计量。
 * <p>
 * 只需装饰 {@code call(EmbeddingRequest)}——EmbeddingModel 的 {@code embed(String)} /
 * {@code embed(List)} / VectorStore 入库用的批量 embed 默认实现最终都经由 call()，故查询
 * 向量化、入库、重嵌等所有真实路径统一被捕获。dimensions() 直接委托（避免探测维度也计一次）。
 */
public class AuditingEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final String modelName;
    private final AiCallAuditor auditor;

    public AuditingEmbeddingModel(EmbeddingModel delegate, String modelName, AiCallAuditor auditor) {
        this.delegate = delegate;
        this.modelName = modelName;
        this.auditor = auditor;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        long start = System.currentTimeMillis();
        try {
            EmbeddingResponse resp = delegate.call(request);
            auditor.recordEmbedding(modelName, tokensOf(resp, request),
                    System.currentTimeMillis() - start, AiCallAuditor.CallStatus.OK);
            return resp;
        } catch (RuntimeException e) {
            auditor.recordEmbedding(modelName, 0, System.currentTimeMillis() - start,
                    AiCallAuditor.CallStatus.ERROR);
            throw e;
        }
    }

    @Override
    public float[] embed(Document document) {
        // 单文档 embed 非本平台实际使用路径（入库走批量 embed → call()）；直接委托即可。
        return delegate.embed(document);
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }

    /**
     * token 数：供应商回报了 usage 就用真实值；否则按输入文本估算——Spring AI 对
     * OpenAI 兼容 embedding 多返回 EmptyUsage（total=0），估算保证 EMBEDDING 消耗可见。
     */
    private static long tokensOf(EmbeddingResponse resp, EmbeddingRequest request) {
        Usage usage = resp != null && resp.getMetadata() != null ? resp.getMetadata().getUsage() : null;
        long reported = usage != null && usage.getTotalTokens() != null ? usage.getTotalTokens() : 0;
        return reported > 0 ? reported : estimateTokens(request);
    }

    /** 估算 token：CJK 约 1 token/字，其余约 1 token/4 字符（够作成本量级参考）。 */
    private static long estimateTokens(EmbeddingRequest request) {
        long cjk = 0, other = 0;
        for (String text : request.getInstructions()) {
            if (text == null) {
                continue;
            }
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c >= 0x4E00 && c <= 0x9FFF) {
                    cjk++;
                } else if (!Character.isWhitespace(c)) {
                    other++;
                }
            }
        }
        return cjk + (other + 3) / 4;
    }
}

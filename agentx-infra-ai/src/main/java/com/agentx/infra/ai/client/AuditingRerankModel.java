package com.agentx.infra.ai.client;

import com.agentx.infra.ai.audit.AiCallAuditor;
import java.util.List;

/**
 * RerankModel 审计装饰器：每次 rerank 记一条 RERANK 审计（模型名/token/延迟/状态），
 * 与 CHAT/EMBEDDING 分开计量。rerank API 多不回报 token，按输入（query + 各候选文本）
 * 估算——cross-encoder 对 query×每个候选各算一遍，输入总长是成本的合理代理。
 */
public class AuditingRerankModel implements RerankModel {

    private final RerankModel delegate;
    private final String modelName;
    private final AiCallAuditor auditor;

    public AuditingRerankModel(RerankModel delegate, String modelName, AiCallAuditor auditor) {
        this.delegate = delegate;
        this.modelName = modelName;
        this.auditor = auditor;
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topN) {
        long start = System.currentTimeMillis();
        try {
            List<RerankResult> out = delegate.rerank(query, documents, topN);
            auditor.recordRerank(modelName, estimateTokens(query, documents),
                    System.currentTimeMillis() - start, AiCallAuditor.CallStatus.OK);
            return out;
        } catch (RuntimeException e) {
            auditor.recordRerank(modelName, 0, System.currentTimeMillis() - start,
                    AiCallAuditor.CallStatus.ERROR);
            throw e;
        }
    }

    /** 估算 token：query 与每个候选文本都进模型，故计 query×N + 各候选之和（CJK~1/字，其余~1/4字符）。 */
    private static long estimateTokens(String query, List<String> documents) {
        long queryTokens = estimateOne(query);
        long docsTokens = 0;
        for (String d : documents) {
            docsTokens += estimateOne(d);
        }
        return queryTokens * Math.max(1, documents.size()) + docsTokens;
    }

    private static long estimateOne(String s) {
        if (s == null) {
            return 0;
        }
        long cjk = 0, other = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                cjk++;
            } else if (!Character.isWhitespace(c)) {
                other++;
            }
        }
        return cjk + (other + 3) / 4;
    }
}

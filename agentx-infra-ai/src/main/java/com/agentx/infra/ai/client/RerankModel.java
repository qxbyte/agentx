package com.agentx.infra.ai.client;

import java.util.List;

/**
 * 重排（rerank）模型抽象：给定查询与一组候选文档，用 cross-encoder 精算相关性并排序。
 * 检索质量层的最后一环——多路召回（多查询 + RRF）负责"别漏"，rerank 负责"排对"。
 * 与知识库来源无关：对本地 + 外部合并后的候选池统一精排。
 */
public interface RerankModel {

    /**
     * 对 documents 按与 query 的相关性重排，返回前 topN 条。
     * 结果的 index 指向入参 documents 的下标，relevanceScore 为 [0,1] 相关度（越大越相关），
     * 已按相关度降序。
     */
    List<RerankResult> rerank(String query, List<String> documents, int topN);

    record RerankResult(int index, double relevanceScore) {}
}

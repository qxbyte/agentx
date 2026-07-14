package com.agentx.rag.retrieval;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF（Reciprocal Rank Fusion）文档融合：score = Σ 1/(k + rank)。
 * <p>
 * 多查询扩展下，每个查询变体各自检索（每次检索已在 composite 层合并本地+外部结果）
 * 得到一个排名列表；本融合器按各文档在各列表中的**名次**累加 RRF 分，跨查询、跨来源
 * 统一收敛为一个候选池。用名次而非原始分融合——对分数尺度不敏感，天然消化"本地
 * PGVector 分与外部余弦分不可比"的问题。去重以片段正文为准（同一 chunk 被多个查询
 * 命中即累加、异构来源同文亦合并）。输出按融合分降序并截断到 topK，且回写归一化分数
 * 供前端展示（顺序与展示分一致）。
 * <p>
 * 单查询时退化为对单个列表做 RRF——等价保序 + 截断，行为一致。
 * 后续接入 rerank 时，本融合器的 topK 放宽为"候选池宽度"，由 rerank 精排收窄到最终 topK。
 */
public class ReciprocalRankFusionJoiner implements DocumentJoiner {

    /** RRF 常数：抑制头部名次的绝对优势，经验值 60。 */
    private static final int K = 60;

    private final int topK;

    public ReciprocalRankFusionJoiner(int topK) {
        this.topK = Math.max(1, topK);
    }

    @Override
    public List<Document> join(Map<Query, List<List<Document>>> documentsForQuery) {
        Map<String, Double> fusedScore = new LinkedHashMap<>();
        Map<String, Document> repr = new LinkedHashMap<>();
        for (List<List<Document>> lists : documentsForQuery.values()) {
            for (List<Document> ranked : lists) {
                for (int rank = 0; rank < ranked.size(); rank++) {
                    Document d = ranked.get(rank);
                    String key = fusionKey(d);
                    fusedScore.merge(key, 1.0 / (K + rank + 1), Double::sum);
                    repr.putIfAbsent(key, d);
                }
            }
        }
        double max = fusedScore.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        return fusedScore.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> withScore(repr.get(e.getKey()), e.getValue() / max))
                .toList();
    }

    /** 去重键：以片段正文为准，跨查询/来源识别"同一片段"，不依赖各源的 id 方案。 */
    private static String fusionKey(Document d) {
        String text = d.getText();
        return text == null || text.isBlank() ? d.getId() : text;
    }

    /** 回写展示分（归一化融合分）：保持展示分与融合顺序一致，避免"排前的反而分低"。 */
    private static Document withScore(Document d, double score) {
        return Document.builder()
                .id(d.getId())
                .text(d.getText())
                .metadata(d.getMetadata())
                .score(score)
                .build();
    }
}

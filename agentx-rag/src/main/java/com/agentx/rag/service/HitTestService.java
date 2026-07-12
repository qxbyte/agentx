package com.agentx.rag.service;

import com.agentx.rag.domain.KnowledgeBase;
import com.agentx.rag.vector.VectorMetadata;
import com.agentx.rag.vector.VectorStoreFactory;
import com.agentx.rag.web.dto.RagDtos.HitTestRequest;
import com.agentx.rag.web.dto.RagDtos.HitView;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

/**
 * 命中测试（设计文档 §4.7 五件套之五）：运营输入问题 → 查看 Top-K 命中分段与分数
 * → 就地修正分段。MaxKB 式"测-看-改"闭环的后端。
 */
@Service
@RequiredArgsConstructor
public class HitTestService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final VectorStoreFactory vectorStoreFactory;

    public List<HitView> hitTest(UUID kbId, UUID userId, HitTestRequest req) {
        KnowledgeBase kb = knowledgeBaseService.getOwned(kbId, userId);
        int topK = req.topK() != null ? req.topK() : kb.getTopK();
        double threshold = req.similarityThreshold() != null
                ? req.similarityThreshold() : kb.getSimilarityThreshold();
        List<Document> hits = vectorStoreFactory.forKb(kb).similaritySearch(
                SearchRequest.builder()
                        .query(req.query())
                        .topK(topK)
                        .similarityThreshold(threshold)
                        .filterExpression(new FilterExpressionBuilder()
                                .eq(VectorMetadata.KB_ID, kbId.toString()).build())
                        .build());
        return hits.stream().map(HitView::of).toList();
    }
}

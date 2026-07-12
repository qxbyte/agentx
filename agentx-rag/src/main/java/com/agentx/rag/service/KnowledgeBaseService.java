package com.agentx.rag.service;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.common.util.UuidV7;
import com.agentx.rag.domain.KnowledgeBase;
import com.agentx.rag.domain.KnowledgeBaseRepository;
import com.agentx.rag.web.dto.RagDtos.KbUpsertRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository repository;

    public List<KnowledgeBase> list(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public KnowledgeBase getOwned(UUID id, UUID userId) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "知识库不存在"));
    }

    /** 内部消费（检索链路）：不做属主校验，属主控制在会话/Agent 绑定入口完成。 */
    public KnowledgeBase getInternal(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "知识库不存在"));
    }

    @Transactional
    public KnowledgeBase create(UUID userId, KbUpsertRequest req) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(UuidV7.next());
        kb.setUserId(userId);
        apply(kb, req);
        return repository.save(kb);
    }

    @Transactional
    public KnowledgeBase update(UUID id, UUID userId, KbUpsertRequest req) {
        KnowledgeBase kb = getOwned(id, userId);
        apply(kb, req);
        return repository.save(kb);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        repository.delete(getOwned(id, userId));
    }

    private void apply(KnowledgeBase kb, KbUpsertRequest req) {
        kb.setName(req.name());
        kb.setDescription(req.description() == null ? "" : req.description());
        kb.setEmbeddingModelId(req.embeddingModelId());
        if (req.chunkSize() != null) {
            kb.setChunkSize(req.chunkSize());
        }
        if (req.chunkOverlap() != null) {
            kb.setChunkOverlap(req.chunkOverlap());
        }
        if (req.topK() != null) {
            kb.setTopK(req.topK());
        }
        if (req.similarityThreshold() != null) {
            kb.setSimilarityThreshold(req.similarityThreshold());
        }
    }
}

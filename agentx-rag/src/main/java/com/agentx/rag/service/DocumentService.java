package com.agentx.rag.service;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.common.util.UuidV7;
import com.agentx.rag.domain.KbDocument;
import com.agentx.rag.domain.KbSegment;
import com.agentx.rag.domain.KnowledgeBase;
import com.agentx.rag.domain.RagIngestTask;
import com.agentx.rag.domain.KbDocumentRepository;
import com.agentx.rag.domain.KbSegmentRepository;
import com.agentx.rag.domain.RagIngestTaskRepository;
import com.agentx.rag.ingest.RagIngestService;
import com.agentx.rag.vector.VectorMetadata;
import com.agentx.rag.vector.VectorStoreFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** 文档与分段管理：上传即触发摄取；删除同步清理分段与向量（双写一致性）。 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final KbDocumentRepository documentRepository;
    private final KbSegmentRepository segmentRepository;
    private final RagIngestTaskRepository taskRepository;
    private final DocumentStorage storage;
    private final RagIngestService ingestService;
    private final VectorStoreFactory vectorStoreFactory;

    @Transactional
    public KbDocument upload(UUID kbId, UUID userId, MultipartFile file) {
        KnowledgeBase kb = knowledgeBaseService.getOwned(kbId, userId);
        KbDocument doc = new KbDocument();
        doc.setId(UuidV7.next());
        doc.setKbId(kb.getId());
        doc.setFilename(file.getOriginalFilename());
        doc.setMimeType(file.getContentType());
        doc.setSizeBytes(file.getSize());
        Path stored = storage.store(kb.getId(), doc.getId(), file);
        doc.setFilePath(stored.toString());
        documentRepository.save(doc);
        ingestService.submit(doc.getId());
        return doc;
    }

    public List<KbDocument> list(UUID kbId, UUID userId) {
        knowledgeBaseService.getOwned(kbId, userId);
        return documentRepository.findByKbIdOrderByCreatedAtDesc(kbId);
    }

    public KbDocument getOwned(UUID docId, UUID userId) {
        KbDocument doc = documentRepository.findById(docId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "文档不存在"));
        knowledgeBaseService.getOwned(doc.getKbId(), userId);
        return doc;
    }

    @Transactional
    public void delete(UUID docId, UUID userId) {
        KbDocument doc = getOwned(docId, userId);
        KnowledgeBase kb = knowledgeBaseService.getInternal(doc.getKbId());
        segmentRepository.deleteByDocId(docId);
        vectorStoreFactory.forKb(kb).delete(new FilterExpressionBuilder()
                .eq(VectorMetadata.DOC_ID, docId.toString()).build());
        storage.deleteQuietly(doc.getFilePath());
        documentRepository.delete(doc);
    }

    public RagIngestTask reingest(UUID docId, UUID userId) {
        getOwned(docId, userId);
        return ingestService.retry(docId);
    }

    public RagIngestTask latestTask(UUID docId, UUID userId) {
        getOwned(docId, userId);
        return taskRepository.findFirstByDocIdOrderByCreatedAtDesc(docId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "无摄取任务"));
    }

    public List<KbSegment> segments(UUID docId, UUID userId) {
        getOwned(docId, userId);
        return segmentRepository.findByDocIdOrderBySeqNoAsc(docId);
    }

    /** 编辑分段内容：更新真源 + 重嵌该段向量。 */
    @Transactional
    public KbSegment updateSegment(UUID segmentId, UUID userId, String content) {
        KbSegment segment = ownedSegment(segmentId, userId);
        segment.setContent(content);
        segment.setCharCount(content.length());
        segmentRepository.save(segment);
        KbDocument doc = documentRepository.findById(segment.getDocId()).orElseThrow();
        KnowledgeBase kb = knowledgeBaseService.getInternal(segment.getKbId());
        var store = vectorStoreFactory.forKb(kb);
        store.delete(new FilterExpressionBuilder()
                .eq(VectorMetadata.SEGMENT_ID, segmentId.toString()).build());
        if (segment.isEnabled()) {
            store.add(List.of(RagIngestService.toVectorDocument(doc, segment)));
        }
        return segment;
    }

    /** 启停分段：禁用删向量保留真源行；启用时补回向量。 */
    @Transactional
    public KbSegment toggleSegment(UUID segmentId, UUID userId, boolean enabled) {
        KbSegment segment = ownedSegment(segmentId, userId);
        segment.setEnabled(enabled);
        segmentRepository.save(segment);
        KbDocument doc = documentRepository.findById(segment.getDocId()).orElseThrow();
        KnowledgeBase kb = knowledgeBaseService.getInternal(segment.getKbId());
        var store = vectorStoreFactory.forKb(kb);
        store.delete(new FilterExpressionBuilder()
                .eq(VectorMetadata.SEGMENT_ID, segmentId.toString()).build());
        if (enabled) {
            store.add(List.of(RagIngestService.toVectorDocument(doc, segment)));
        }
        return segment;
    }

    private KbSegment ownedSegment(UUID segmentId, UUID userId) {
        KbSegment segment = segmentRepository.findById(segmentId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "分段不存在"));
        knowledgeBaseService.getOwned(segment.getKbId(), userId);
        return segment;
    }
}

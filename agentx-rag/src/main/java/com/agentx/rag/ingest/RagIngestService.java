package com.agentx.rag.ingest;

import com.agentx.common.util.UuidV7;
import com.agentx.rag.domain.KbDocument;
import com.agentx.rag.domain.KbSegment;
import com.agentx.rag.domain.KnowledgeBase;
import com.agentx.rag.domain.RagIngestTask;
import com.agentx.rag.domain.KbDocumentRepository;
import com.agentx.rag.domain.KbSegmentRepository;
import com.agentx.rag.domain.KnowledgeBaseRepository;
import com.agentx.rag.domain.RagIngestTaskRepository;
import com.agentx.rag.vector.VectorMetadata;
import com.agentx.rag.vector.VectorStoreFactory;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 异步向量化流水线（设计文档 §8.3）：解析 → 分段 → 双写（segment 表 + 向量库）。
 * 虚拟线程执行；任务状态与进度落 rag_ingest_task，失败可重试；
 * 重复摄取先清本文档旧分段与旧向量，幂等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagIngestService {

    private static final int EMBED_BATCH_SIZE = 16;

    private final KnowledgeBaseRepository kbRepository;
    private final KbDocumentRepository documentRepository;
    private final KbSegmentRepository segmentRepository;
    private final RagIngestTaskRepository taskRepository;
    private final VectorStoreFactory vectorStoreFactory;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /** 提交摄取任务（立即返回任务 ID，后台执行）。 */
    public RagIngestTask submit(UUID docId) {
        RagIngestTask task = new RagIngestTask();
        task.setId(UuidV7.next());
        task.setDocId(docId);
        taskRepository.save(task);
        dispatchAfterCommit(task.getId());
        return task;
    }

    /**
     * 调用方（上传）处于事务中：任务行与文档行尚未提交，后台线程立刻执行会读不到数据。
     * 必须挂到 afterCommit 再派发；无事务上下文（如重试端点非事务路径）则直接执行。
     */
    private void dispatchAfterCommit(UUID taskId) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(
                            new org.springframework.transaction.support.TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    executor.submit(() -> runSafely(taskId));
                                }
                            });
        } else {
            executor.submit(() -> runSafely(taskId));
        }
    }

    private void runSafely(UUID taskId) {
        try {
            run(taskId);
        } catch (Exception e) {
            log.error("摄取任务执行异常 task={}", taskId, e);
        }
    }

    void run(UUID taskId) {
        RagIngestTask task = taskRepository.findById(taskId).orElseThrow();
        KbDocument doc = documentRepository.findById(task.getDocId()).orElseThrow();
        KnowledgeBase kb = kbRepository.findById(doc.getKbId()).orElseThrow();
        try {
            transition(task, RagIngestTask.Status.RUNNING, 0);
            updateDocStatus(doc, KbDocument.Status.PARSING);

            String text = parse(doc);
            List<String> chunks = splitterFor(doc, kb).split(text);

            updateDocStatus(doc, KbDocument.Status.INGESTING);
            cleanupPrevious(kb, doc);

            List<KbSegment> segments = persistSegments(doc, chunks);
            embedInBatches(kb, doc, segments, task);

            doc.setSegmentCount(segments.size());
            updateDocStatus(doc, KbDocument.Status.READY);
            transition(task, RagIngestTask.Status.SUCCEEDED, 100);
            log.info("摄取完成 doc={} segments={}", doc.getId(), segments.size());
        } catch (Exception e) {
            log.warn("摄取失败 doc={}: {}", doc.getId(), e.getMessage(), e);
            updateDocStatus(doc, KbDocument.Status.FAILED);
            task.setErrorMsg(abbreviate(e.getMessage()));
            transition(task, RagIngestTask.Status.FAILED, task.getProgress());
        }
    }

    /** 失败重试：新任务，retries+1。 */
    public RagIngestTask retry(UUID docId) {
        RagIngestTask previous = taskRepository.findFirstByDocIdOrderByCreatedAtDesc(docId)
                .orElse(null);
        RagIngestTask task = submit(docId);
        task.setRetries(previous == null ? 0 : previous.getRetries() + 1);
        return taskRepository.save(task);
    }

    /** Markdown 走结构感知切片（标题/围栏/表格边界），其余类型走通用句读窗口切分。 */
    private static TextSplitter splitterFor(KbDocument doc, KnowledgeBase kb) {
        String name = doc.getFilename() == null ? "" : doc.getFilename().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".markdown")
                ? new MarkdownStructureSplitter(kb.getChunkSize(), kb.getChunkOverlap())
                : new OverlappingTextSplitter(kb.getChunkSize(), kb.getChunkOverlap());
    }

    private String parse(KbDocument doc) {
        List<Document> parsed = new TikaDocumentReader(
                new FileSystemResource(doc.getFilePath())).get();
        StringBuilder sb = new StringBuilder();
        parsed.forEach(d -> sb.append(d.getText()).append('\n'));
        return sb.toString();
    }

    private void cleanupPrevious(KnowledgeBase kb, KbDocument doc) {
        segmentRepository.deleteByDocId(doc.getId());
        vectorStoreFactory.forKb(kb).delete(new FilterExpressionBuilder()
                .eq(VectorMetadata.DOC_ID, doc.getId().toString()).build());
    }

    private List<KbSegment> persistSegments(KbDocument doc, List<String> chunks) {
        List<KbSegment> segments = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            KbSegment segment = new KbSegment();
            segment.setId(UuidV7.next());
            segment.setDocId(doc.getId());
            segment.setKbId(doc.getKbId());
            segment.setSeqNo(i);
            segment.setContent(chunks.get(i));
            segment.setCharCount(chunks.get(i).length());
            segments.add(segment);
        }
        return segmentRepository.saveAll(segments);
    }

    private void embedInBatches(KnowledgeBase kb, KbDocument doc,
                                List<KbSegment> segments, RagIngestTask task) {
        VectorStore store = vectorStoreFactory.forKb(kb);
        for (int from = 0; from < segments.size(); from += EMBED_BATCH_SIZE) {
            List<KbSegment> batch = segments.subList(from,
                    Math.min(from + EMBED_BATCH_SIZE, segments.size()));
            store.add(batch.stream().map(s -> toVectorDocument(doc, s)).toList());
            transition(task, RagIngestTask.Status.RUNNING,
                    (int) ((from + batch.size()) * 100.0 / segments.size()));
        }
    }

    /** 分段 → 向量文档（含检索/溯源全部元数据）。 */
    public static Document toVectorDocument(KbDocument doc, KbSegment segment) {
        return Document.builder()
                .id(segment.getId().toString())
                .text(segment.getContent())
                .metadata(Map.of(
                        VectorMetadata.KB_ID, segment.getKbId().toString(),
                        VectorMetadata.DOC_ID, segment.getDocId().toString(),
                        VectorMetadata.SEGMENT_ID, segment.getId().toString(),
                        VectorMetadata.DOC_NAME, doc.getFilename()))
                .build();
    }

    private void transition(RagIngestTask task, RagIngestTask.Status status, int progress) {
        task.setStatus(status);
        task.setProgress(progress);
        if (status == RagIngestTask.Status.SUCCEEDED || status == RagIngestTask.Status.FAILED) {
            task.setFinishedAt(Instant.now());
        }
        taskRepository.save(task);
    }

    private void updateDocStatus(KbDocument doc, KbDocument.Status status) {
        doc.setStatus(status);
        documentRepository.save(doc);
    }

    private static String abbreviate(String message) {
        if (message == null) {
            return "unknown";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}

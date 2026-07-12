package com.agentx.rag.web.dto;

import com.agentx.rag.domain.KbDocument;
import com.agentx.rag.domain.KbSegment;
import com.agentx.rag.domain.KnowledgeBase;
import com.agentx.rag.domain.RagIngestTask;
import com.agentx.rag.vector.VectorMetadata;
import jakarta.validation.constraints.NotBlank;
import org.springframework.ai.document.Document;
import java.time.Instant;
import java.util.UUID;

public final class RagDtos {
    private RagDtos() {}

    public record KbUpsertRequest(@NotBlank String name, String description,
                                  UUID embeddingModelId, Integer chunkSize, Integer chunkOverlap,
                                  Integer topK, Double similarityThreshold) {}

    public record KbView(UUID id, String name, String description, UUID embeddingModelId,
                         int chunkSize, int chunkOverlap, int topK, double similarityThreshold,
                         Instant createdAt) {
        public static KbView of(KnowledgeBase kb) {
            return new KbView(kb.getId(), kb.getName(), kb.getDescription(),
                    kb.getEmbeddingModelId(), kb.getChunkSize(), kb.getChunkOverlap(),
                    kb.getTopK(), kb.getSimilarityThreshold(), kb.getCreatedAt());
        }
    }

    public record DocView(UUID id, UUID kbId, String filename, String mimeType, long sizeBytes,
                          KbDocument.Status status, int segmentCount, Instant createdAt) {
        public static DocView of(KbDocument d) {
            return new DocView(d.getId(), d.getKbId(), d.getFilename(), d.getMimeType(),
                    d.getSizeBytes(), d.getStatus(), d.getSegmentCount(), d.getCreatedAt());
        }
    }

    public record SegmentView(UUID id, UUID docId, int seqNo, String content, int charCount,
                              boolean enabled) {
        public static SegmentView of(KbSegment s) {
            return new SegmentView(s.getId(), s.getDocId(), s.getSeqNo(), s.getContent(),
                    s.getCharCount(), s.isEnabled());
        }
    }

    public record TaskView(UUID id, UUID docId, RagIngestTask.Status status, int progress,
                           String errorMsg, int retries, Instant createdAt, Instant finishedAt) {
        public static TaskView of(RagIngestTask t) {
            return new TaskView(t.getId(), t.getDocId(), t.getStatus(), t.getProgress(),
                    t.getErrorMsg(), t.getRetries(), t.getCreatedAt(), t.getFinishedAt());
        }
    }

    public record SegmentUpdateRequest(@NotBlank String content) {}

    public record HitTestRequest(@NotBlank String query, Integer topK, Double similarityThreshold) {}

    public record HitView(String segmentId, String docId, String docName, String content, Double score) {
        public static HitView of(Document doc) {
            return new HitView(
                    String.valueOf(doc.getMetadata().get(VectorMetadata.SEGMENT_ID)),
                    String.valueOf(doc.getMetadata().get(VectorMetadata.DOC_ID)),
                    String.valueOf(doc.getMetadata().get(VectorMetadata.DOC_NAME)),
                    doc.getText(),
                    doc.getScore());
        }
    }
}

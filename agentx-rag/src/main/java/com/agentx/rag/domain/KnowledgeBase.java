package com.agentx.rag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "kb_knowledge_base")
public class KnowledgeBase {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description = "";

    @Column(name = "embedding_model_id")
    private UUID embeddingModelId;

    @Column(name = "chunk_size", nullable = false)
    private int chunkSize = 800;

    @Column(name = "chunk_overlap", nullable = false)
    private int chunkOverlap = 80;

    @Column(name = "top_k", nullable = false)
    private int topK = 5;

    @Column(name = "similarity_threshold", nullable = false)
    private double similarityThreshold = 0.0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

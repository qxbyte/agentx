package com.agentx.rag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "rag_ingest_task")
public class RagIngestTask {

    public enum Status { PENDING, RUNNING, SUCCEEDED, FAILED }

    @Id
    private UUID id;

    @Column(name = "doc_id", nullable = false)
    private UUID docId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int progress;

    @Column(name = "error_msg")
    private String errorMsg;

    @Column(nullable = false)
    private int retries;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;
}

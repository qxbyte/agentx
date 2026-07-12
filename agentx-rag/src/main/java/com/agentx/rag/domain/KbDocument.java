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
@Table(name = "kb_document")
public class KbDocument {

    public enum Status { UPLOADED, PARSING, INGESTING, READY, FAILED }

    @Id
    private UUID id;

    @Column(name = "kb_id", nullable = false)
    private UUID kbId;

    @Column(nullable = false)
    private String filename;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.UPLOADED;

    @Column(name = "segment_count", nullable = false)
    private int segmentCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

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
@Table(name = "kb_segment")
public class KbSegment {
    @Id
    private UUID id;

    @Column(name = "doc_id", nullable = false)
    private UUID docId;

    @Column(name = "kb_id", nullable = false)
    private UUID kbId;

    @Column(name = "seq_no", nullable = false)
    private int seqNo;

    @Column(nullable = false)
    private String content;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

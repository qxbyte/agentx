package com.agentx.rag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/**
 * 外部知识库（固定三 API 模板接入：heartbeat / info / search）。
 * vaultId 是外部系统内的仓库标识——检索必带，防多仓库内容互相污染；
 * enabled=false 时检索完全跳过（解耦开关）。
 */
@Getter
@Setter
@Entity
@Table(name = "external_kb")
public class ExternalKb {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "vault_id", nullable = false)
    private String vaultId;

    @Column(name = "heartbeat_path", nullable = false)
    private String heartbeatPath = "/api/external-kb/heartbeat";

    @Column(name = "info_path", nullable = false)
    private String infoPath = "/api/external-kb/info";

    @Column(name = "search_path", nullable = false)
    private String searchPath = "/api/external-kb/search";

    @Column(name = "top_k", nullable = false)
    private int topK = 5;

    @Column(name = "similarity_threshold", nullable = false)
    private double similarityThreshold = 0.2;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

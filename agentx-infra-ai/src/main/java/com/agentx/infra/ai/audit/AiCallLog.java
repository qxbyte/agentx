package com.agentx.infra.ai.audit;

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
@Table(name = "ai_call_log")
public class AiCallLog {
    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "model_name")
    private String modelName;

    /** 模型类型：CHAT | EMBEDDING，用于与语言模型分开计量。 */
    @Column(name = "model_type", nullable = false)
    private String modelType = "CHAT";

    @Column(name = "prompt_tokens", nullable = false)
    private long promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private long completionTokens;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

package com.agentx.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "chat_message")
public class ChatMessage {
    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Column(nullable = false)
    private String content = "";

    @Column(name = "reasoning_content")
    private String reasoningContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_calls", columnDefinition = "jsonb")
    private String toolCalls;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rag_sources", columnDefinition = "jsonb")
    private String ragSources;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "token_usage", columnDefinition = "jsonb")
    private String tokenUsage;

    /** 用户消息的附件元数据（[{id, filename, sizeBytes}]），供历史气泡渲染附件芯片 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String attachments;

    /**
     * 用户消息的入忆版文本（skill 展开/附件占位后）：与用户原文不同时才存，
     * 重新生成回滚记忆时按此保真重建。不进历史接口（MessageView 不映射）。
     */
    @Column(name = "model_content")
    private String modelContent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

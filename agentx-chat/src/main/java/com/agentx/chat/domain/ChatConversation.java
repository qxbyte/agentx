package com.agentx.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "chat_conversation")
public class ChatConversation {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title = "新对话";

    @Column(name = "agent_id")
    private UUID agentId;

    /** 绑定的知识库 ID 集合（JSON 数组），M4 消费。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "kb_ids", columnDefinition = "jsonb")
    private String kbIds;

    @Column(name = "model_config_id")
    private UUID modelConfigId;

    /** CodeAgent：会话归属的编码项目（工作区）；null 为普通对话。供侧栏按项目分组。 */
    @Column(name = "workspace_id")
    private UUID workspaceId;

    /** updatePlan 工具最近一次调用的参数原文（计划步骤 JSON），供前端恢复计划面板。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plan_state", columnDefinition = "jsonb")
    private String planState;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}

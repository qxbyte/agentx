package com.agentx.agent.domain;

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
@Table(name = "agent_definition")
public class AgentDefinition {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String description = "";

    @Column(name = "system_prompt", nullable = false)
    private String systemPrompt;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_type", nullable = false)
    private WorkflowType workflowType = WorkflowType.REACT;

    /** 绑定工具名 JSON 数组，如 ["currentWeather","query"]。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_names", columnDefinition = "jsonb")
    private String toolNames;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "kb_ids", columnDefinition = "jsonb")
    private String kbIds;

    @Column(name = "model_config_id")
    private UUID modelConfigId;

    @Column(name = "max_iterations", nullable = false)
    private int maxIterations = 8;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

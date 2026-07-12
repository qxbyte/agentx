package com.agentx.tools.registry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/**
 * 工具运行态（tool_registry 表）：定义来自代码/MCP（运行时事实），
 * 表只持久化"运营可变"的部分——启用开关与来源归属。
 */
@Getter
@Setter
@Entity
@Table(name = "tool_registry")
public class ToolState {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String description;

    @Column(name = "params_schema")
    private String paramsSchema;

    @Column(name = "mcp_server_id")
    private UUID mcpServerId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

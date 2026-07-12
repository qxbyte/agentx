package com.agentx.mcp.domain;

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
@Table(name = "mcp_server_config")
public class McpServerConfig {

    public enum Transport { STDIO, STREAMABLE_HTTP }

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Transport transport;

    /** STDIO: {command,args[],env{}}；STREAMABLE_HTTP: {url,headers{}} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "connect_params", columnDefinition = "jsonb", nullable = false)
    private String connectParams;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "last_health_at")
    private Instant lastHealthAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

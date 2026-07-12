package com.agentx.mcp.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface McpServerConfigRepository extends JpaRepository<McpServerConfig, UUID> {
    List<McpServerConfig> findByEnabledTrue();
    Optional<McpServerConfig> findByName(String name);
}

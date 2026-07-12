package com.agentx.mcp.web.dto;

import com.agentx.mcp.domain.McpServerConfig;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class McpDtos {
    private McpDtos() {}

    public record UpsertRequest(@NotBlank String name,
                                @NotNull McpServerConfig.Transport transport,
                                @NotNull Map<String, Object> connectParams,
                                boolean enabled) {}

    public record View(UUID id, String name, McpServerConfig.Transport transport,
                       String connectParams, boolean enabled, Instant lastHealthAt,
                       Instant createdAt) {
        public static View of(McpServerConfig c) {
            return new View(c.getId(), c.getName(), c.getTransport(), c.getConnectParams(),
                    c.isEnabled(), c.getLastHealthAt(), c.getCreatedAt());
        }
    }

    public record RemoteToolView(String name, String description) {
        public static List<RemoteToolView> of(McpSchema.ListToolsResult result) {
            return result.tools().stream()
                    .map(t -> new RemoteToolView(t.name(), t.description()))
                    .toList();
        }
    }
}

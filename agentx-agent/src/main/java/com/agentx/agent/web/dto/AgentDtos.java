package com.agentx.agent.web.dto;

import com.agentx.agent.domain.AgentDefinition;
import com.agentx.agent.domain.WorkflowType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AgentDtos {
    private AgentDtos() {}

    public record UpsertRequest(@NotBlank String name, String description,
                                @NotBlank String systemPrompt,
                                @NotNull WorkflowType workflowType,
                                List<String> toolNames, List<UUID> kbIds,
                                UUID modelConfigId, Integer maxIterations, boolean enabled) {}

    public record View(UUID id, String name, String description, String systemPrompt,
                       WorkflowType workflowType, String toolNames, String kbIds,
                       UUID modelConfigId, int maxIterations, boolean enabled, String source,
                       String pluginId, Instant createdAt) {
        public static View of(AgentDefinition a) {
            return new View(a.getId(), a.getName(), a.getDescription(), a.getSystemPrompt(),
                    a.getWorkflowType(), a.getToolNames(), a.getKbIds(), a.getModelConfigId(),
                    a.getMaxIterations(), a.isEnabled(), a.getSource(), a.getPluginId(),
                    a.getCreatedAt());
        }
    }
}

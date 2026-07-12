package com.agentx.infra.ai.web.dto;

import com.agentx.infra.ai.model.ModelType;
import com.agentx.infra.ai.model.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class ModelConfigDtos {
    private ModelConfigDtos() {}

    public record UpsertRequest(@NotBlank String name, @NotNull ProviderType providerType,
                                String baseUrl, String apiKey,
                                @NotBlank String modelName, @NotNull ModelType type,
                                boolean enabled) {}

    public record View(UUID id, String name, ProviderType providerType, String baseUrl,
                       String maskedApiKey, String modelName, ModelType type,
                       boolean defaultModel, boolean enabled, Instant createdAt) {}
}

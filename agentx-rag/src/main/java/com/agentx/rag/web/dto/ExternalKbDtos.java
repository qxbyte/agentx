package com.agentx.rag.web.dto;

import com.agentx.rag.domain.ExternalKb;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public final class ExternalKbDtos {
    private ExternalKbDtos() {}

    public record UpsertRequest(@NotBlank String name, @NotBlank String baseUrl,
                                @NotBlank String vaultId,
                                String heartbeatPath, String infoPath, String searchPath,
                                Integer topK, Double similarityThreshold,
                                boolean enabled) {}

    public record View(UUID id, String name, String baseUrl, String vaultId,
                       String heartbeatPath, String infoPath, String searchPath,
                       int topK, double similarityThreshold, boolean enabled, Instant createdAt) {
        public static View of(ExternalKb kb) {
            return new View(kb.getId(), kb.getName(), kb.getBaseUrl(), kb.getVaultId(),
                    kb.getHeartbeatPath(), kb.getInfoPath(), kb.getSearchPath(),
                    kb.getTopK(), kb.getSimilarityThreshold(), kb.isEnabled(), kb.getCreatedAt());
        }
    }

    /** 连接探测结果：alive + 库信息 + embedding 一致性提醒（warning 非空即需注意）。 */
    public record ProbeResult(boolean alive, String service, String vaultName,
                              String embeddingModel, int dims, int chunkCount,
                              String error, String warning) {}
}

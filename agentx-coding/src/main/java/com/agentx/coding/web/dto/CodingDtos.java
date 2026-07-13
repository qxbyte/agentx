package com.agentx.coding.web.dto;

import com.agentx.coding.domain.CodingWorkspace;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public final class CodingDtos {
    private CodingDtos() {}

    public record WorkspaceUpsertRequest(@NotBlank String name, @NotBlank String rootPath, UUID kbId) {}

    public record WorkspaceView(UUID id, String name, String rootPath, UUID kbId, Instant createdAt) {
        public static WorkspaceView of(CodingWorkspace ws) {
            return new WorkspaceView(ws.getId(), ws.getName(), ws.getRootPath(), ws.getKbId(),
                    ws.getCreatedAt());
        }
    }

    public record ValidateRequest(@NotBlank String rootPath) {}

    public record WorkspaceValidation(boolean exists, boolean writable, boolean gitRepo, String message) {}
}

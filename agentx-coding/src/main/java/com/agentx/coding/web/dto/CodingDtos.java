package com.agentx.coding.web.dto;

import com.agentx.coding.domain.CodingWorkspace;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public final class CodingDtos {
    private CodingDtos() {}

    public record WorkspaceUpsertRequest(@NotBlank String name, @NotBlank String rootPath, UUID kbId) {}

    /** 新建空白项目：仅项目名（目录由后端在受控根下创建）+ 可选默认知识库。 */
    public record BlankWorkspaceRequest(@NotBlank String name, UUID kbId) {}

    public record WorkspaceView(UUID id, String name, String rootPath, UUID kbId, Instant createdAt) {
        public static WorkspaceView of(CodingWorkspace ws) {
            return new WorkspaceView(ws.getId(), ws.getName(), ws.getRootPath(), ws.getKbId(),
                    ws.getCreatedAt());
        }
    }

    public record ValidateRequest(@NotBlank String rootPath) {}

    public record WorkspaceValidation(boolean exists, boolean writable, boolean gitRepo, String message) {}
}

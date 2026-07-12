package com.agentx.agent.web;

import com.agentx.agent.domain.WorkflowType;
import com.agentx.agent.orchestration.WorkflowRunner;
import com.agentx.common.api.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** workflow 演示/调试端点（ADMIN）：同步执行一次编排。 */
@RestController
@RequiredArgsConstructor
public class WorkflowController {
    private final WorkflowRunner workflowRunner;

    public record RunRequest(@NotBlank String input) {}

    @PostMapping("/api/v1/admin/workflows/{type}/run")
    public ApiResponse<String> run(@PathVariable WorkflowType type,
                                   @RequestBody RunRequest req) {
        return ApiResponse.ok(workflowRunner.run(type, req.input()));
    }
}

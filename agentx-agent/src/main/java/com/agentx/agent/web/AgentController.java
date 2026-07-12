package com.agentx.agent.web;

import com.agentx.agent.service.AgentDefinitionService;
import com.agentx.agent.web.dto.AgentDtos.UpsertRequest;
import com.agentx.agent.web.dto.AgentDtos.View;
import com.agentx.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AgentController {
    private final AgentDefinitionService service;

    /** 用户侧：可选用的启用 Agent 列表。 */
    @GetMapping("/api/v1/agents")
    public ApiResponse<List<View>> listEnabled() {
        return ApiResponse.ok(service.listEnabled().stream().map(View::of).toList());
    }

    @GetMapping("/api/v1/admin/agents")
    public ApiResponse<List<View>> listAll() {
        return ApiResponse.ok(service.listAll().stream().map(View::of).toList());
    }

    @PostMapping("/api/v1/admin/agents")
    public ApiResponse<View> create(@Valid @RequestBody UpsertRequest req) {
        return ApiResponse.ok(View.of(service.create(req)));
    }

    @PutMapping("/api/v1/admin/agents/{id}")
    public ApiResponse<View> update(@PathVariable UUID id, @Valid @RequestBody UpsertRequest req) {
        return ApiResponse.ok(View.of(service.update(id, req)));
    }

    @DeleteMapping("/api/v1/admin/agents/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}

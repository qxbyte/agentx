package com.agentx.mcp.web;

import com.agentx.common.api.ApiResponse;
import com.agentx.mcp.service.McpServerService;
import com.agentx.mcp.web.dto.McpDtos.RemoteToolView;
import com.agentx.mcp.web.dto.McpDtos.UpsertRequest;
import com.agentx.mcp.web.dto.McpDtos.View;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/mcp-servers")
@RequiredArgsConstructor
public class McpServerController {

    private final McpServerService service;

    @GetMapping
    public ApiResponse<List<View>> list() {
        return ApiResponse.ok(service.list().stream().map(View::of).toList());
    }

    @PostMapping
    public ApiResponse<View> create(@Valid @RequestBody UpsertRequest req) {
        return ApiResponse.ok(View.of(service.create(req)));
    }

    @PutMapping("/{id}")
    public ApiResponse<View> update(@PathVariable UUID id, @Valid @RequestBody UpsertRequest req) {
        return ApiResponse.ok(View.of(service.update(id, req)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/test-connection")
    public ApiResponse<List<RemoteToolView>> testConnection(@PathVariable UUID id) {
        return ApiResponse.ok(RemoteToolView.of(service.testConnection(id)));
    }

    @GetMapping("/{id}/tools")
    public ApiResponse<List<RemoteToolView>> tools(@PathVariable UUID id) {
        return ApiResponse.ok(RemoteToolView.of(service.remoteTools(id)));
    }
}

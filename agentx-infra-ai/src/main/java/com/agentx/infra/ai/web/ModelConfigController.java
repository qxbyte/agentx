package com.agentx.infra.ai.web;

import com.agentx.common.api.ApiResponse;
import com.agentx.infra.ai.model.ModelConfigService;
import com.agentx.infra.ai.web.dto.ModelConfigDtos.UpsertRequest;
import com.agentx.infra.ai.web.dto.ModelConfigDtos.View;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/model-configs")
@RequiredArgsConstructor
public class ModelConfigController {
    private final ModelConfigService service;

    @GetMapping
    public ApiResponse<List<View>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<View> create(@Valid @RequestBody UpsertRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<View> update(@PathVariable UUID id, @Valid @RequestBody UpsertRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PatchMapping("/{id}/default")
    public ApiResponse<View> markDefault(@PathVariable UUID id) {
        return ApiResponse.ok(service.markDefault(id));
    }
}

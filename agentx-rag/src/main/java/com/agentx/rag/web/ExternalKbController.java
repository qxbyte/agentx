package com.agentx.rag.web;

import com.agentx.common.api.ApiResponse;
import com.agentx.rag.domain.ExternalKb;
import com.agentx.rag.service.ExternalKbService;
import com.agentx.rag.web.dto.ExternalKbDtos.ProbeResult;
import com.agentx.rag.web.dto.ExternalKbDtos.UpsertRequest;
import com.agentx.rag.web.dto.ExternalKbDtos.View;
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

/** 外部知识库管理（设置页）：CRUD + 连接探测（含 embedding 一致性提醒）。 */
@RestController
@RequestMapping("/api/v1/admin/external-kbs")
@RequiredArgsConstructor
public class ExternalKbController {

    private final ExternalKbService service;

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

    /** 连接探测：按已存配置（或编辑中的参数）测 heartbeat+info，返回一致性提醒。 */
    @PostMapping("/{id}/test")
    public ApiResponse<ProbeResult> test(@PathVariable UUID id) {
        ExternalKb kb = service.get(id);
        return ApiResponse.ok(service.probe(kb.getBaseUrl(), kb.getHeartbeatPath(),
                kb.getInfoPath(), kb.getVaultId()));
    }

    /** 保存前探测（新建表单用）：直接以请求参数探测，不落库。 */
    @PostMapping("/probe")
    public ApiResponse<ProbeResult> probe(@Valid @RequestBody UpsertRequest req) {
        String hb = req.heartbeatPath() == null || req.heartbeatPath().isBlank()
                ? "/api/external-kb/heartbeat" : req.heartbeatPath();
        String info = req.infoPath() == null || req.infoPath().isBlank()
                ? "/api/external-kb/info" : req.infoPath();
        return ApiResponse.ok(service.probe(req.baseUrl().replaceAll("/+$", ""), hb, info, req.vaultId()));
    }
}

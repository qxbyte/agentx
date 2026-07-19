package com.agentx.coding.web;

import com.agentx.auth.security.AuthPrincipal;
import com.agentx.auth.security.CurrentUser;
import com.agentx.coding.service.WorkspaceService;
import com.agentx.coding.web.dto.CodingDtos.BlankWorkspaceRequest;
import com.agentx.coding.web.dto.CodingDtos.ValidateRequest;
import com.agentx.coding.web.dto.CodingDtos.WorkspaceUpsertRequest;
import com.agentx.coding.web.dto.CodingDtos.WorkspaceValidation;
import com.agentx.coding.web.dto.CodingDtos.WorkspaceView;
import com.agentx.common.api.ApiResponse;
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
@RequestMapping("/api/v1/coding/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService service;

    @GetMapping
    public ApiResponse<List<WorkspaceView>> list(@CurrentUser AuthPrincipal user) {
        return ApiResponse.ok(service.list(user.id()).stream().map(WorkspaceView::of).toList());
    }

    @PostMapping
    public ApiResponse<WorkspaceView> create(@CurrentUser AuthPrincipal user,
                                             @Valid @RequestBody WorkspaceUpsertRequest req) {
        return ApiResponse.ok(WorkspaceView.of(service.create(user.id(), req)));
    }

    @PutMapping("/{id}")
    public ApiResponse<WorkspaceView> update(@CurrentUser AuthPrincipal user, @PathVariable UUID id,
                                             @Valid @RequestBody WorkspaceUpsertRequest req) {
        return ApiResponse.ok(WorkspaceView.of(service.update(id, user.id(), req)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@CurrentUser AuthPrincipal user, @PathVariable UUID id) {
        service.delete(id, user.id());
        return ApiResponse.ok();
    }

    @PostMapping("/validate")
    public ApiResponse<WorkspaceValidation> validate(@Valid @RequestBody ValidateRequest req) {
        return ApiResponse.ok(service.validate(req.rootPath()));
    }

    /** 新建空白项目：后端在受控根下建目录并 git init。 */
    @PostMapping("/blank")
    public ApiResponse<WorkspaceView> createBlank(@CurrentUser AuthPrincipal user,
                                                  @Valid @RequestBody BlankWorkspaceRequest req) {
        return ApiResponse.ok(WorkspaceView.of(service.createBlank(user.id(), req.name(), req.kbId())));
    }
}

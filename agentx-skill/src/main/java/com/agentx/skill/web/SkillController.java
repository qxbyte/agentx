package com.agentx.skill.web;

import com.agentx.common.api.ApiResponse;
import com.agentx.skill.service.SkillService;
import com.agentx.skill.web.dto.SkillDtos.Detail;
import com.agentx.skill.web.dto.SkillDtos.EnabledRequest;
import com.agentx.skill.web.dto.SkillDtos.Meta;
import com.agentx.skill.web.dto.SkillDtos.UpsertRequest;
import com.agentx.skill.web.dto.SkillDtos.View;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * Skill 斜杠命令管理。skill 是本机目录化配置（~/.agentx/skills/：
 * 不随账号走），登录用户即可读写；name 即资源标识。
 */
@RestController
@RequiredArgsConstructor
public class SkillController {

    private final SkillService service;

    /** 启用中的元数据（/ 补全菜单，渐进式披露 L1：永不含 content）。 */
    @GetMapping("/api/v1/skills")
    public ApiResponse<List<Meta>> listEnabled() {
        return ApiResponse.ok(service.listEnabled().stream().map(Meta::of).toList());
    }

    /** 管理列表（含停用，不含 content）。 */
    @GetMapping("/api/v1/skills/all")
    public ApiResponse<List<View>> listAll() {
        return ApiResponse.ok(service.listAll().stream().map(View::of).toList());
    }

    /** 详情（含 content，编辑用）。 */
    @GetMapping("/api/v1/skills/{name}")
    public ApiResponse<Detail> get(@PathVariable String name) {
        return ApiResponse.ok(Detail.of(service.getOwned(name)));
    }

    @PostMapping("/api/v1/skills")
    public ApiResponse<Detail> create(@Valid @RequestBody UpsertRequest req) {
        return ApiResponse.ok(Detail.of(service.create(req)));
    }

    @PutMapping("/api/v1/skills/{name}")
    public ApiResponse<Detail> update(@PathVariable String name,
                                      @Valid @RequestBody UpsertRequest req) {
        return ApiResponse.ok(Detail.of(service.update(name, req)));
    }

    @PatchMapping("/api/v1/skills/{name}/enabled")
    public ApiResponse<View> setEnabled(@PathVariable String name,
                                        @RequestBody EnabledRequest req) {
        return ApiResponse.ok(View.of(service.setEnabled(name, req.enabled())));
    }

    @DeleteMapping("/api/v1/skills/{name}")
    public ApiResponse<Void> delete(@PathVariable String name) {
        service.delete(name);
        return ApiResponse.ok();
    }
}

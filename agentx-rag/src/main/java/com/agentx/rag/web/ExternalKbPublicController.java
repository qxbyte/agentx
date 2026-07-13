package com.agentx.rag.web;

import com.agentx.common.api.ApiResponse;
import com.agentx.rag.service.ExternalKbService;
import com.agentx.rag.web.dto.ExternalKbDtos.View;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 面向用户的外部知识库列表（仅启用项）：输入框知识库选择器与本地库并列展示。 */
@RestController
@RequestMapping("/api/v1/kb/external")
@RequiredArgsConstructor
public class ExternalKbPublicController {

    private final ExternalKbService service;

    @GetMapping
    public ApiResponse<List<View>> listEnabled() {
        return ApiResponse.ok(service.listEnabled().stream().map(View::of).toList());
    }
}

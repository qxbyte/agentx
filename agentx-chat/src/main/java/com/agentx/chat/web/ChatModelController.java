package com.agentx.chat.web;

import com.agentx.common.api.ApiResponse;
import com.agentx.infra.ai.model.ModelConfigService;
import com.agentx.infra.ai.web.dto.ModelConfigDtos.ModelOption;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 面向用户的可选模型列表（非 admin）：供对话/编码输入框的模型选择器使用。
 * 只暴露启用中的 CHAT 模型的 id / 名称 / 模型名，不含密钥与 baseUrl。
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatModelController {

    private final ModelConfigService modelConfigService;

    @GetMapping("/models")
    public ApiResponse<List<ModelOption>> models() {
        return ApiResponse.ok(modelConfigService.listSelectableChat());
    }
}

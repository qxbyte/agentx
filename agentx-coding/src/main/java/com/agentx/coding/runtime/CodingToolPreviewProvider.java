package com.agentx.coding.runtime;

import com.agentx.infra.ai.stream.ToolPreviewProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * 编码工具的帧富化实现（{@link ToolPreviewProvider}）：把 kind/preview 注入 tool-call/tool-result 帧。
 * 逻辑与 {@link ApprovalGate} 共用 {@link CodingToolPreviews}，仅 CodingStreamCustomizer 注入使用，
 * 普通对话与 agent 会话不受影响。
 */
@Component
@RequiredArgsConstructor
public class CodingToolPreviewProvider implements ToolPreviewProvider {

    private final ObjectMapper objectMapper;

    @Override
    public String kindOf(String toolName) {
        return CodingToolPreviews.kindOf(toolName);
    }

    @Override
    public Map<String, Object> previewOf(String toolName, String argsJson) {
        return CodingToolPreviews.previewOf(toolName, argsJson, objectMapper);
    }
}

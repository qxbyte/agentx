package com.agentx.coding.runtime;

import com.agentx.infra.ai.stream.ApprovalRegistry;
import com.agentx.infra.ai.stream.ChatStreamContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 审批装饰器实现：把危险工具包进 {@link ApprovalGate}。 */
@Component
@RequiredArgsConstructor
public class CodingApprovalDecoratorImpl implements CodingApprovalDecorator {

    private final ApprovalRegistry approvalRegistry;
    private final CodingModeRegistry modeRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public ToolCallback decorate(ToolCallback delegate, ChatStreamContext context) {
        return new ApprovalGate(delegate, context, approvalRegistry, modeRegistry, objectMapper);
    }
}

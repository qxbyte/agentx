package com.agentx.coding.runtime;

import com.agentx.infra.ai.stream.ApprovalRegistry;
import com.agentx.infra.ai.stream.ChatStreamContext;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 审批网关装饰器（设计文档 §6）：危险工具执行前发 approval-request 帧并阻塞等回传。
 * 套在 {@link com.agentx.infra.ai.stream.SseNotifyingToolCallback} 外层——
 * 批准后才进入内层（发 tool-call → 执行 → tool-result）；拒绝/超时则把结果返回给模型，不执行。
 */
public class ApprovalGate implements ToolCallback {

    private static final long DEFAULT_TIMEOUT_MILLIS = 10 * 60 * 1000L;

    private final ToolCallback delegate;
    private final ChatStreamContext context;
    private final ApprovalRegistry registry;
    private final ObjectMapper objectMapper;
    /** 审批等待上限（毫秒）；可注入以便单测用短超时。 */
    private final long timeoutMillis;

    public ApprovalGate(ToolCallback delegate, ChatStreamContext context,
                        ApprovalRegistry registry, ObjectMapper objectMapper) {
        this(delegate, context, registry, objectMapper, DEFAULT_TIMEOUT_MILLIS);
    }

    public ApprovalGate(ToolCallback delegate, ChatStreamContext context,
                        ApprovalRegistry registry, ObjectMapper objectMapper, long timeoutMillis) {
        this.delegate = delegate;
        this.context = context;
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String toolName = delegate.getToolDefinition().name();
        UUID approvalId = UUID.randomUUID();
        UUID conversationId = context.conversationId();

        String kind = CodingToolPreviews.kindOf(toolName);
        Map<String, Object> preview = CodingToolPreviews.previewOf(toolName, toolInput, objectMapper);

        CompletableFuture<Boolean> future = registry.register(context.userId(), conversationId, approvalId);
        context.toolEventSink().onApprovalRequest(approvalId.toString(), toolName, kind, preview);

        boolean approved;
        try {
            approved = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            registry.forget(conversationId, approvalId);
            // 权威终态帧：超时/会话结束也要翻转前端审批卡，避免卡片停在 pending 可点
            context.toolEventSink().onApprovalResult(approvalId.toString(), "expired");
            return "操作未获批准（审批超时或会话结束），已跳过：" + toolName;
        }
        registry.forget(conversationId, approvalId);
        // 权威终态帧：无论批准/拒绝都下发，前端卡片终态以此为准（不依赖回传请求的响应）
        context.toolEventSink().onApprovalResult(approvalId.toString(), approved ? "approved" : "rejected");

        if (!approved) {
            return "用户拒绝了此操作（" + toolName + "）。请调整方案或询问用户。";
        }
        // 批准 → 进入内层装饰（发 tool-call / 执行 / tool-result）
        return toolContext == null ? delegate.call(toolInput) : delegate.call(toolInput, toolContext);
    }
}

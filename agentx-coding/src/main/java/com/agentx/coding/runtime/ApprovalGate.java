package com.agentx.coding.runtime;

import com.agentx.infra.ai.stream.ApprovalRegistry;
import com.agentx.infra.ai.stream.ChatStreamContext;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
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

    private static final long APPROVAL_TIMEOUT_MINUTES = 10;

    private final ToolCallback delegate;
    private final ChatStreamContext context;
    private final ApprovalRegistry registry;
    private final ObjectMapper objectMapper;

    public ApprovalGate(ToolCallback delegate, ChatStreamContext context,
                        ApprovalRegistry registry, ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.context = context;
        this.registry = registry;
        this.objectMapper = objectMapper;
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

        String kind = kindOf(toolName);
        Map<String, Object> preview = buildPreview(toolName, toolInput);

        CompletableFuture<Boolean> future = registry.register(conversationId, approvalId);
        context.toolEventSink().onApprovalRequest(approvalId.toString(), toolName, kind, preview);

        boolean approved;
        try {
            approved = future.get(APPROVAL_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            registry.forget(conversationId, approvalId);
            return "操作未获批准（审批超时或会话结束），已跳过：" + toolName;
        }
        registry.forget(conversationId, approvalId);

        if (!approved) {
            return "用户拒绝了此操作（" + toolName + "）。请调整方案或询问用户。";
        }
        // 批准 → 进入内层装饰（发 tool-call / 执行 / tool-result）
        return toolContext == null ? delegate.call(toolInput) : delegate.call(toolInput, toolContext);
    }

    private String kindOf(String toolName) {
        return switch (toolName) {
            case "applyPatch" -> "patch";
            case "runShell" -> "shell";
            case "gitCommit" -> "commit";
            case "writeFile" -> "write";
            default -> "generic";
        };
    }

    /** 从工具入参 JSON 提取结构化预览，前端按 kind 渲染 diff / 命令 / 文件。 */
    private Map<String, Object> buildPreview(String toolName, String toolInput) {
        Map<String, Object> args = parseArgs(toolInput);
        Map<String, Object> preview = new LinkedHashMap<>();
        switch (toolName) {
            case "applyPatch" -> preview.put("diff", str(args.get("unifiedDiff")));
            case "runShell" -> preview.put("command", str(args.get("command")));
            case "gitCommit" -> preview.put("message", str(args.get("message")));
            case "writeFile" -> {
                preview.put("path", str(args.get("path")));
                preview.put("content", str(args.get("content")));
            }
            default -> preview.put("args", toolInput);
        }
        return preview;
    }

    private Map<String, Object> parseArgs(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(toolInput, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }
}

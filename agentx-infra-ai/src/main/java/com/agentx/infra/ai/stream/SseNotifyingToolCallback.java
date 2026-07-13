package com.agentx.infra.ai.stream;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工具回调装饰器：执行前后向 {@link ToolEventSink} 发事件（前端过程可视化），
 * 并承担工具循环上限守卫——2.0 核心未提供 maxIterations API，此处以
 * 每请求共享计数实现：超限后向模型返回明确的终止提示，促使其直接作答。
 * 装饰器方案不侵入框架，任何来源（代码/MCP）的工具一视同仁。
 */
public class SseNotifyingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolEventSink sink;
    private final AtomicInteger sharedCallCounter;
    private final int maxToolCalls;
    /** 帧富化策略；null 表示不富化（普通对话），帧退化为纯文本。 */
    private final ToolPreviewProvider previewProvider;

    public SseNotifyingToolCallback(ToolCallback delegate, ToolEventSink sink,
                                    AtomicInteger sharedCallCounter, int maxToolCalls) {
        this(delegate, sink, sharedCallCounter, maxToolCalls, null);
    }

    public SseNotifyingToolCallback(ToolCallback delegate, ToolEventSink sink,
                                    AtomicInteger sharedCallCounter, int maxToolCalls,
                                    ToolPreviewProvider previewProvider) {
        this.delegate = delegate;
        this.sink = sink;
        this.sharedCallCounter = sharedCallCounter;
        this.maxToolCalls = maxToolCalls;
        this.previewProvider = previewProvider;
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
        String name = delegate.getToolDefinition().name();
        String callId = UUID.randomUUID().toString().substring(0, 8);
        String kind = previewProvider == null ? null : previewProvider.kindOf(name);
        if (sharedCallCounter.incrementAndGet() > maxToolCalls) {
            String halt = "已达到本次对话的工具调用上限（" + maxToolCalls + " 次），请基于已有信息直接回答。";
            sink.onToolResult(callId, name, halt, kind);
            return halt;
        }
        Map<String, Object> preview = previewProvider == null
                ? null : previewProvider.previewOf(name, toolInput);
        sink.onToolCall(callId, name, toolInput, kind, preview);
        try {
            String result = toolContext == null
                    ? delegate.call(toolInput)
                    : delegate.call(toolInput, toolContext);
            sink.onToolResult(callId, name, result, kind);
            return result;
        } catch (Exception e) {
            // 工具异常包装为结果返给模型，让其向用户解释——不断流（设计文档 §9）
            String error = "工具执行失败: " + e.getMessage();
            sink.onToolResult(callId, name, error, kind);
            return error;
        }
    }
}

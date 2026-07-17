package com.agentx.agent.runtime;

import com.agentx.infra.ai.stream.ChatStreamContext;
import com.agentx.infra.ai.stream.ChatStreamCustomizer;
import com.agentx.infra.ai.stream.SseNotifyingToolCallback;
import com.agentx.tools.builtin.files.FileTools;
import com.agentx.tools.registry.ToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件生成工具全局注入：所有会话绑定 generateDocument / generateSpreadsheet，
 * 触发时机由工具 description 约束（用户明确要求文件才生成）。
 * AgentStreamCustomizer 侧已过滤同名工具避免重复绑定。
 */
@Order(16)
@Component
@RequiredArgsConstructor
public class FileStreamCustomizer implements ChatStreamCustomizer {

    /** 单轮文件生成上限：防模型失控批量刷文件。 */
    private static final int MAX_FILE_CALLS = 8;

    private final ToolRegistry toolRegistry;

    @Override
    public void customize(ChatStreamContext context, ChatClient.ChatClientRequestSpec spec) {
        List<ToolCallback> tools = toolRegistry.resolve(
                List.of(FileTools.DOC_TOOL, FileTools.SHEET_TOOL));
        if (tools.isEmpty()) {
            return;
        }
        AtomicInteger counter = new AtomicInteger();
        spec.toolCallbacks(tools.stream()
                .<ToolCallback>map(t -> new SseNotifyingToolCallback(
                        t, context.toolEventSink(), counter, MAX_FILE_CALLS))
                .toList());
    }
}

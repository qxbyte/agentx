package com.agentx.chat.service.memory;

import com.agentx.chat.tools.AttachmentTools;
import com.agentx.chat.tools.MemoryTools;
import com.agentx.infra.ai.stream.ChatStreamContext;
import com.agentx.infra.ai.stream.ChatStreamCustomizer;
import com.agentx.infra.ai.stream.SseNotifyingToolCallback;
import com.agentx.tools.registry.ToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 记忆类工具全局注入（模式同 PlanStreamCustomizer）：所有会话绑定
 * readAttachment（附件按需重读）与 saveMemory（长期记忆写入），
 * 由模型依 description 自行判断调用时机。AgentStreamCustomizer 侧已过滤同名，避免重复绑定。
 */
@Order(17)
@Component
@RequiredArgsConstructor
public class MemoryToolsCustomizer implements ChatStreamCustomizer {

    /** 调用守卫上限：分页重读长文档可能多次调用，给足余量。 */
    private static final int MAX_CALLS = 30;

    private final ToolRegistry toolRegistry;

    @Override
    public void customize(ChatStreamContext context, ChatClient.ChatClientRequestSpec spec) {
        List<ToolCallback> tools = toolRegistry.resolve(
                List.of(AttachmentTools.READ_TOOL, MemoryTools.SAVE_TOOL));
        if (tools.isEmpty()) {
            return;
        }
        AtomicInteger counter = new AtomicInteger();
        spec.toolCallbacks(tools.stream()
                .<ToolCallback>map(t -> new SseNotifyingToolCallback(
                        t, context.toolEventSink(), counter, MAX_CALLS))
                .toList());
    }
}

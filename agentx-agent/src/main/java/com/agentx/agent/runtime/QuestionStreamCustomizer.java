package com.agentx.agent.runtime;

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
 * 提问工具全局注入：所有会话一律绑定 askUserQuestion，模型依工具 description
 * 的保守约束自行判断何时向用户提问（机制同 PlanStreamCustomizer）。
 */
@Order(17)
@Component
@RequiredArgsConstructor
public class QuestionStreamCustomizer implements ChatStreamCustomizer {

    public static final String QUESTION_TOOL = "askUserQuestion";
    /** 单次对话提问调用上限：防模型反复打断用户。 */
    private static final int MAX_QUESTION_CALLS = 5;

    private final ToolRegistry toolRegistry;

    @Override
    public void customize(ChatStreamContext context, ChatClient.ChatClientRequestSpec spec) {
        List<ToolCallback> tools = toolRegistry.resolve(List.of(QUESTION_TOOL));
        if (tools.isEmpty()) {
            return;
        }
        AtomicInteger counter = new AtomicInteger();
        spec.toolCallbacks(tools.stream()
                .<ToolCallback>map(t -> new SseNotifyingToolCallback(
                        t, context.toolEventSink(), counter, MAX_QUESTION_CALLS))
                .toList());
    }
}

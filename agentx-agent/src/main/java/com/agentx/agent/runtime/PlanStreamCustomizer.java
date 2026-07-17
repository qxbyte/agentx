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
 * 计划工具全局注入：所有会话（普通/Agent/编码）一律绑定 updatePlan，
 * 由模型依工具 description 中的保守约束自行判断是否启用（大任务才拆分）。
 * AgentStreamCustomizer 侧已过滤 updatePlan，避免同名工具重复绑定。
 */
@Order(15)
@Component
@RequiredArgsConstructor
public class PlanStreamCustomizer implements ChatStreamCustomizer {

    public static final String PLAN_TOOL = "updatePlan";
    /** 计划更新调用守卫上限：每步一次更新，50 步足够覆盖超长任务。 */
    private static final int MAX_PLAN_CALLS = 50;

    private final ToolRegistry toolRegistry;

    @Override
    public void customize(ChatStreamContext context, ChatClient.ChatClientRequestSpec spec) {
        List<ToolCallback> tools = toolRegistry.resolve(List.of(PLAN_TOOL));
        if (tools.isEmpty()) {
            return;
        }
        AtomicInteger counter = new AtomicInteger();
        spec.toolCallbacks(tools.stream()
                .<ToolCallback>map(t -> new SseNotifyingToolCallback(
                        t, context.toolEventSink(), counter, MAX_PLAN_CALLS))
                .toList());
    }
}

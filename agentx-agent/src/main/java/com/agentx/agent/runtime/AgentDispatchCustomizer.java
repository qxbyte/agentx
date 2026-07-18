package com.agentx.agent.runtime;

import com.agentx.agent.domain.AgentDefinition;
import com.agentx.agent.service.AgentDefinitionService;
import com.agentx.infra.ai.client.ChatClientFactory;
import com.agentx.infra.ai.stream.ChatStreamContext;
import com.agentx.infra.ai.stream.ChatStreamCustomizer;
import com.agentx.infra.ai.stream.SseNotifyingToolCallback;
import com.agentx.tools.registry.ToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 子代理派遣工具全局注入：有启用的 Agent 定义（含插件贡献的子代理）时绑定
 * dispatchAgent,目录随工具 description 下发,模型自行判断何时派遣。
 */
@Order(19)
@Component
@RequiredArgsConstructor
public class AgentDispatchCustomizer implements ChatStreamCustomizer {

    /** 单次对话派遣上限：子代理调用成本高,防失控连环派遣。 */
    private static final int MAX_DISPATCHES = 3;

    private final AgentDefinitionService agentService;
    private final ToolRegistry toolRegistry;
    private final ChatClientFactory chatClientFactory;
    private final ObjectMapper objectMapper;

    @Override
    public void customize(ChatStreamContext context, ChatClient.ChatClientRequestSpec spec) {
        List<AgentDefinition> agents = agentService.listEnabled();
        if (agents.isEmpty()) {
            return;
        }
        AtomicInteger counter = new AtomicInteger();
        DispatchAgentTool tool = new DispatchAgentTool(
                agentService, toolRegistry, chatClientFactory, objectMapper, agents);
        spec.toolCallbacks(List.of(new SseNotifyingToolCallback(
                tool, context.toolEventSink(), counter, MAX_DISPATCHES)));
    }
}

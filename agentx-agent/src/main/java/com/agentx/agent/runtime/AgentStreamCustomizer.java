package com.agentx.agent.runtime;

import com.agentx.agent.domain.AgentDefinition;
import com.agentx.agent.service.AgentDefinitionService;
import com.agentx.infra.ai.stream.ChatStreamContext;
import com.agentx.infra.ai.stream.ChatStreamCustomizer;
import com.agentx.tools.registry.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 会话定制（设计文档 §8.2）：会话绑定 Agent 时注入
 * system prompt + 解析后的工具集（经 SSE 事件装饰 + 循环守卫）。
 */
@Slf4j
@Order(10)
@Component
@RequiredArgsConstructor
public class AgentStreamCustomizer implements ChatStreamCustomizer {

    private final AgentDefinitionService agentDefinitionService;
    private final ToolRegistry toolRegistry;

    @Override
    public void customize(ChatStreamContext context, ChatClient.ChatClientRequestSpec spec) {
        if (context.agentId() == null) {
            return;
        }
        AgentDefinition agent = agentDefinitionService.getEnabled(context.agentId());
        if (StringUtils.hasText(agent.getSystemPrompt())) {
            spec.system(agent.getSystemPrompt());
        }
        // Agent 绑定的知识库合并进上下文，由 RagStreamCustomizer（@Order 靠后）统一消费
        agentDefinitionService.kbIdsOf(agent).forEach(context.kbIds()::add);
        List<ToolCallback> tools = toolRegistry.resolve(agentDefinitionService.toolNamesOf(agent));
        if (!tools.isEmpty()) {
            AtomicInteger counter = new AtomicInteger();
            List<ToolCallback> decorated = tools.stream()
                    .<ToolCallback>map(t -> new SseNotifyingToolCallback(
                            t, context.toolEventSink(), counter, agent.getMaxIterations()))
                    .toList();
            spec.toolCallbacks(decorated);
        }
        log.debug("agent 定制生效: {} tools={}", agent.getName(), tools.size());
    }
}

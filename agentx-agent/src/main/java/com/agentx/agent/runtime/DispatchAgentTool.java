package com.agentx.agent.runtime;

import com.agentx.agent.domain.AgentDefinition;
import com.agentx.agent.service.AgentDefinitionService;
import com.agentx.infra.ai.client.ChatClientFactory;
import com.agentx.tools.registry.ToolRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * dispatchAgent 工具（对标 Claude Code 的子代理派遣）：主模型把一个独立子任务
 * 交给某个 Agent 定义执行——子代理以自己的 system prompt 与工具集跑一次嵌套
 * 调用（独立上下文,不共享主对话历史）,最终文本作为工具结果回注主对话。
 * 子代理不再携带 dispatchAgent,天然杜绝递归派遣。
 */
class DispatchAgentTool implements ToolCallback {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{
            "agent":{"type":"string","description":"子代理名称(见目录,如 task-swarm:task-swarm-reviewer)"},
            "task":{"type":"string","description":"交给子代理的完整任务描述:自包含,含必要上下文与期望产出"}},
            "required":["agent","task"]}""";
    private static final int DESC_LIMIT = 100;
    private static final int RESULT_LIMIT = 8_000;

    private final AgentDefinitionService agentService;
    private final ToolRegistry toolRegistry;
    private final ChatClientFactory chatClientFactory;
    private final ObjectMapper objectMapper;
    private final ToolDefinition definition;

    DispatchAgentTool(AgentDefinitionService agentService, ToolRegistry toolRegistry,
                      ChatClientFactory chatClientFactory, ObjectMapper objectMapper,
                      List<AgentDefinition> agents) {
        this.agentService = agentService;
        this.toolRegistry = toolRegistry;
        this.chatClientFactory = chatClientFactory;
        this.objectMapper = objectMapper;
        this.definition = DefaultToolDefinition.builder()
                .name("dispatchAgent")
                .description(buildDescription(agents))
                .inputSchema(INPUT_SCHEMA)
                .build();
    }

    private static String buildDescription(List<AgentDefinition> agents) {
        StringBuilder sb = new StringBuilder("""
                把一个独立的子任务派遣给专职子代理执行,其最终结论作为结果返回。\
                适合需要专门角色/技能处理、且可独立完成的子任务(如代码审查、专项分析);\
                任务描述必须自包含——子代理看不到本对话历史。简单任务不要派遣,直接完成。可用子代理:
                """);
        for (AgentDefinition agent : agents) {
            String desc = agent.getDescription() == null ? "" : agent.getDescription();
            if (desc.length() > DESC_LIMIT) {
                desc = desc.substring(0, DESC_LIMIT) + "…";
            }
            sb.append("- ").append(agent.getName()).append(": ").append(desc).append('\n');
        }
        return sb.toString();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String agentName;
        String task;
        try {
            JsonNode node = objectMapper.readTree(toolInput);
            agentName = node.path("agent").asString("").strip();
            task = node.path("task").asString("").strip();
        } catch (RuntimeException e) {
            return "参数解析失败,请以 {\"agent\":\"名称\",\"task\":\"任务\"} 调用。";
        }
        if (agentName.isEmpty() || task.isEmpty()) {
            return "agent 与 task 均不能为空。";
        }
        AgentDefinition agent = agentService.listEnabled().stream()
                .filter(a -> a.getName().equals(agentName))
                .findFirst().orElse(null);
        if (agent == null) {
            return "子代理不存在或未启用: " + agentName + "。可用子代理见本工具描述中的目录。";
        }

        try {
            ChatClient client = agent.getModelConfigId() != null
                    ? chatClientFactory.get(agent.getModelConfigId())
                    : chatClientFactory.getDefault();
            var prompt = client.prompt()
                    .system(agent.getSystemPrompt())
                    .user(task);
            // 子代理带自己的工具集(静默执行,不发 SSE 帧;派遣本身的 tool-call/result 帧已可见)
            List<ToolCallback> tools = toolRegistry.resolve(agentService.toolNamesOf(agent));
            if (!tools.isEmpty()) {
                prompt = prompt.toolCallbacks(tools);
            }
            String content = prompt.call().content();
            if (content == null || content.isBlank()) {
                return "子代理未产出内容: " + agentName;
            }
            if (content.length() > RESULT_LIMIT) {
                content = content.substring(0, RESULT_LIMIT) + "\n[子代理输出已截断]";
            }
            return "<subagent_result agent=\"%s\">\n%s\n</subagent_result>".formatted(agentName, content);
        } catch (RuntimeException e) {
            return "子代理执行失败(" + agentName + "): " + e.getMessage() + "。请自行完成该子任务或换一种方式。";
        }
    }
}

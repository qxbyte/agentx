package com.agentx.chat.tools;

import com.agentx.chat.service.memory.MemoryFileService;
import com.agentx.tools.registry.AgentTool;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 长期记忆写入工具（显式通道）：模型在对话中识别到值得跨会话记住的信息时主动调用。
 * 存储为 md 文件（用户可直接查看/编辑/删除，透明可控）：
 * 用户级 ~/.agentx/AGENTX.md；项目级 <工作区根>/AGENTX.md（仅编码会话可写）。
 */
@AgentTool(group = "memory")
@RequiredArgsConstructor
public class MemoryTools {

    public static final String SAVE_TOOL = "saveMemory";
    /** 与 coding 模块 WorkspaceContext.WORKSPACE_ROOT 对齐的字面量（chat 不依赖 coding 模块）。 */
    private static final String WORKSPACE_ROOT_KEY = "codingWorkspaceRoot";

    private final MemoryFileService memoryFileService;

    @Tool(description = """
            保存一条跨会话长期记忆。仅当信息在未来会话中仍然有用时才调用：\
            用户的稳定偏好（语言/技术栈/风格）、明确的长期约定、项目的关键决策。\
            scope=user 存用户级记忆；scope=project 存当前项目（仅绑定了项目的会话可用）。\
            不要保存：本次对话就能完成的临时信息、代码里已有的事实、大段原文。""")
    public String saveMemory(
            @ToolParam(description = "user（用户级偏好/事实）或 project（当前项目约定）") String scope,
            @ToolParam(description = "要记住的内容：一句话事实陈述，简短、自包含") String content,
            ToolContext toolContext) {
        if (content == null || content.isBlank()) {
            return "记忆内容不能为空";
        }
        String normalized = scope == null ? "" : scope.strip().toLowerCase();
        try {
            switch (normalized) {
                case "user" -> {
                    memoryFileService.appendUserMemory(content);
                    return "已保存到用户记忆（~/.agentx/AGENTX.md）";
                }
                case "project" -> {
                    Object root = toolContext.getContext().get(WORKSPACE_ROOT_KEY);
                    if (root == null) {
                        return "当前会话未绑定项目，无法保存项目级记忆；如属用户偏好请改用 scope=user";
                    }
                    memoryFileService.appendWorkspaceMemory(root.toString(), content);
                    return "已保存到项目记忆（" + MemoryFileService.MEMORY_FILENAME + "）";
                }
                default -> {
                    return "scope 只能是 user 或 project";
                }
            }
        } catch (IllegalStateException e) {
            return "保存失败：" + e.getMessage();
        }
    }
}

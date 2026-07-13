package com.agentx.coding.runtime;

import com.agentx.coding.domain.CodingWorkspace;
import com.agentx.coding.service.WorkspaceService;
import com.agentx.coding.tools.DangerousTools;
import com.agentx.coding.tools.WorkspaceContext;
import com.agentx.infra.ai.stream.ChatStreamContext;
import com.agentx.infra.ai.stream.ChatStreamCustomizer;
import com.agentx.tools.registry.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

/**
 * CodeAgent 会话定制（设计文档 §5）：会话绑定工作区时注入编码工具集与 system prompt，
 * 按模式过滤工具、经审批网关装饰危险工具。
 * <p>
 * @Order(15)：在 Agent(10) 之后、RAG(20) 之前——把工作区绑定的知识库并入
 * context.kbIds()，使随后的 RagStreamCustomizer 自动检索（chat 模块无需感知
 * workspace.kbId，解耦）。工具注入顺序无关（toolCallbacks 累加）。
 */
@Slf4j
@Order(15)
@Component
@RequiredArgsConstructor
public class CodingStreamCustomizer implements ChatStreamCustomizer {

    /** 全部编码工具名（注册中心里的 @AgentTool 方法名）。 */
    private static final List<String> READONLY_TOOLS =
            List.of("listDir", "readFile", "grepFiles", "findFiles", "gitStatus", "gitDiff");
    private static final List<String> WRITE_TOOLS =
            List.of("writeFile", "applyPatch", "runShell", "gitCommit");

    private final WorkspaceService workspaceService;
    private final ToolRegistry toolRegistry;
    private final CodingApprovalDecorator approvalDecorator;

    @Override
    public void customize(ChatStreamContext context, ChatClient.ChatClientRequestSpec spec) {
        if (context.workspaceId() == null) {
            return;
        }
        CodingWorkspace ws = workspaceService.getOwned(context.workspaceId(), context.userId());
        CodingMode mode = parseMode(context.codingMode());

        // 工作区绑定的知识库并入检索：本定制器 @Order(15) 早于 RAG(20)，加入后由其消费
        if (ws.getKbId() != null) {
            context.kbIds().add(ws.getKbId());
        }

        List<String> toolNames = mode == CodingMode.PLAN
                ? READONLY_TOOLS
                : concat(READONLY_TOOLS, WRITE_TOOLS);
        List<ToolCallback> tools = toolRegistry.resolve(toolNames);

        if (mode == CodingMode.ASK) {
            tools = tools.stream()
                    .map(t -> DangerousTools.isDangerous(t.getToolDefinition().name())
                            ? approvalDecorator.decorate(t, context)
                            : t)
                    .toList();
        }
        if (!tools.isEmpty()) {
            spec.toolCallbacks(tools);
        }

        // 工作区上下文注入 ToolContext（工具经此拿沙箱）
        spec.toolContext(Map.of(
                WorkspaceContext.WORKSPACE_ROOT, ws.getRootPath(),
                WorkspaceContext.WORKSPACE_ID, ws.getId().toString(),
                WorkspaceContext.MODE, mode.name()));

        spec.system(systemPrompt(ws, mode));
        log.debug("coding 定制生效: workspace={} mode={} tools={}", ws.getName(), mode, tools.size());
    }

    private String systemPrompt(CodingWorkspace ws, CodingMode mode) {
        String base = """
                你是 AgentX 的编码助手，在一个受控工作区内处理代码任务（读代码、定位并修复 bug）。
                工作区名：%s。所有文件路径都是相对工作区根的相对路径。
                先用只读工具（listDir/readFile/grepFiles/findFiles）充分理解代码，再动手修改。
                如果绑定了知识库，优先检索其中的规范与背景知识辅助定位问题。
                """.formatted(ws.getName());
        return base + switch (mode) {
            case PLAN -> "当前是【规划模式】：你只能读取与分析，禁止修改文件或执行命令。产出清晰的修复方案供人工审阅。";
            case ASK -> "当前是【审批模式】：修改文件与执行命令前会请求人工确认。请把每一步改动讲清楚，改动尽量小而聚焦。";
            case AUTO -> "当前是【自动模式】：你可以连续读取、修改、执行命令并根据结果迭代，直到完成任务或需要用户澄清。";
        };
    }

    private static List<String> concat(List<String> a, List<String> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }

    private static CodingMode parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return CodingMode.ASK;
        }
        try {
            return CodingMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return CodingMode.ASK;
        }
    }
}

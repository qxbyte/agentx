package com.agentx.coding.runtime;

import com.agentx.coding.tools.DangerousTools;
import com.agentx.coding.tools.WorkspaceContext;
import com.agentx.infra.ai.stream.ChatStreamContext;
import com.agentx.infra.ai.stream.ChatStreamCustomizer;
import com.agentx.infra.ai.stream.SseNotifyingToolCallback;
import com.agentx.tools.registry.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 普通对话的本地"天然工具"（AgentX 是本地 app,模型应有本地文件手——对标
 * Claude Code / Codex 桌面版）：非编码会话注入同一套文件/命令工具,
 * 沙箱根为本机家目录（可配）,Plan/Ask/Auto 模式语义与审批网关全部复用
 * coding 模块既有机制。编码会话(workspaceId 非空)由 CodingStreamCustomizer 接管,
 * 此处不激活,互斥不重复绑定。
 * <p>
 * 与 CodingStreamCustomizer 的差异:不设置 system prompt(避免覆盖 Agent 人格),
 * 模型对路径语义的认知靠工具 description;不含 git 工具(家目录非仓库)。
 */
@Slf4j
@Order(14)
@Component
public class LocalToolsCustomizer implements ChatStreamCustomizer {

    private static final List<String> READONLY_TOOLS =
            List.of("listDir", "readFile", "grepFiles", "findFiles");
    private static final List<String> WRITE_TOOLS =
            List.of("writeFile", "applyPatch", "runShell");
    /** 普通对话的本地工具调用上限（低于编码会话:闲聊场景不该有超长工具循环）。 */
    private static final int MAX_TOOL_CALLS = 40;

    private final ToolRegistry toolRegistry;
    private final CodingApprovalDecorator approvalDecorator;
    private final CodingToolPreviewProvider previewProvider;
    private final CodingModeRegistry modeRegistry;
    private final LocalToolsSettings settings;

    public LocalToolsCustomizer(ToolRegistry toolRegistry,
                                CodingApprovalDecorator approvalDecorator,
                                CodingToolPreviewProvider previewProvider,
                                CodingModeRegistry modeRegistry,
                                LocalToolsSettings settings) {
        this.toolRegistry = toolRegistry;
        this.approvalDecorator = approvalDecorator;
        this.previewProvider = previewProvider;
        this.modeRegistry = modeRegistry;
        this.settings = settings;
    }

    @Override
    public void customize(ChatStreamContext context, ChatClient.ChatClientRequestSpec spec) {
        if (!settings.isEnabled() || context.workspaceId() != null) {
            return;
        }
        CodingMode mode = CodingMode.parseOrDefault(context.codingMode());
        // 播种实时模式表:轮内切换(回传端点)后 ApprovalGate 按最新值放行/拦截
        modeRegistry.seed(context.conversationId(), context.userId(), mode);

        List<String> toolNames = mode == CodingMode.PLAN
                ? READONLY_TOOLS
                : java.util.stream.Stream.concat(READONLY_TOOLS.stream(), WRITE_TOOLS.stream()).toList();

        AtomicInteger counter = new AtomicInteger();
        List<ToolCallback> tools = toolRegistry.resolve(toolNames).stream()
                .<ToolCallback>map(t -> new SseNotifyingToolCallback(
                        t, context.toolEventSink(), counter, MAX_TOOL_CALLS, previewProvider))
                .map(t -> mode == CodingMode.ASK
                        && DangerousTools.isDangerous(t.getToolDefinition().name())
                        ? approvalDecorator.decorate(t, context)
                        : t)
                .toList();
        if (tools.isEmpty()) {
            return;
        }
        spec.toolCallbacks(tools);
        // 沙箱根 = 家目录:相对路径按家目录解析,越界访问被 PathSandbox 拦截
        spec.toolContext(Map.of(
                WorkspaceContext.WORKSPACE_ROOT, settings.getRoot(),
                WorkspaceContext.MODE, mode.name()));
        log.debug("本地工具已注入(普通对话): root={} mode={} tools={}", settings.getRoot(), mode, tools.size());
    }
}

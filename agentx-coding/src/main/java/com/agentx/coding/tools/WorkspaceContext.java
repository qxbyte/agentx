package com.agentx.coding.tools;

import com.agentx.coding.sandbox.PathSandbox;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import org.springframework.ai.chat.model.ToolContext;

/**
 * 从 ToolContext 提取工作区信息的辅助器（设计文档 §5）。
 * 编码工具经此拿到 {@link PathSandbox}，禁止访问 SecurityContext（异步执行无请求线程）。
 */
public final class WorkspaceContext {

    public static final String WORKSPACE_ROOT = "codingWorkspaceRoot";
    public static final String WORKSPACE_ID = "codingWorkspaceId";
    public static final String MODE = "codingMode";

    private WorkspaceContext() {}

    public static PathSandbox sandboxOf(ToolContext toolContext) {
        Object root = context(toolContext).get(WORKSPACE_ROOT);
        if (root == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "当前会话未绑定编码工作区");
        }
        return PathSandbox.of(root.toString());
    }

    private static java.util.Map<String, Object> context(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "缺少工具上下文");
        }
        return toolContext.getContext();
    }
}

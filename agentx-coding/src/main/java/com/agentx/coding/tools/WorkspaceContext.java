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
    /** 只读工具的第二根（家目录）:编码会话可越出工作区读取本机文件。 */
    public static final String LOCAL_READ_ROOT = "codingLocalReadRoot";

    private WorkspaceContext() {}

    public static PathSandbox sandboxOf(ToolContext toolContext) {
        var ctx = context(toolContext);
        Object root = ctx.get(WORKSPACE_ROOT);
        if (root == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "当前会话未绑定编码工作区");
        }
        // BYPASS 模式:无边界沙箱,root 仅作相对路径基准（等同用户本人在终端操作）
        if (isBypass(ctx)) {
            return PathSandbox.unrestricted(root.toString());
        }
        return PathSandbox.of(root.toString());
    }

    /** 只读工具的沙箱:工作区为主根,家目录为只读第二根（未配置则退化严格单根）。 */
    public static PathSandbox readSandboxOf(ToolContext toolContext) {
        var ctx = context(toolContext);
        Object root = ctx.get(WORKSPACE_ROOT);
        if (root == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "当前会话未绑定编码工作区");
        }
        if (isBypass(ctx)) {
            return PathSandbox.unrestricted(root.toString());
        }
        Object secondary = ctx.get(LOCAL_READ_ROOT);
        return PathSandbox.of(root.toString(), secondary == null ? null : secondary.toString());
    }

    /** 当前轮是否 BYPASS 模式（ShellTools 据此切换黑名单档位）。 */
    public static boolean isBypass(ToolContext toolContext) {
        return isBypass(context(toolContext));
    }

    private static boolean isBypass(java.util.Map<String, Object> ctx) {
        return "BYPASS".equals(ctx.get(MODE));
    }

    private static java.util.Map<String, Object> context(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "缺少工具上下文");
        }
        return toolContext.getContext();
    }
}

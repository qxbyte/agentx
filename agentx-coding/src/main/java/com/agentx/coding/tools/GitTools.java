package com.agentx.coding.tools;

import com.agentx.coding.sandbox.PathSandbox;
import com.agentx.tools.registry.AgentTool;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * CodeAgent git 工具（设计文档 §4）。status/diff 只读安全；commit 危险级（审批）。
 * 直接调 git 子进程，工作目录锁定工作区根。
 */
@AgentTool(group = "coding")
public class GitTools {

    private static final long TIMEOUT_SECONDS = 30;

    @Tool(description = "查看工作区的 git 状态（git status --short）")
    public String gitStatus(ToolContext toolContext) {
        return run(WorkspaceContext.sandboxOf(toolContext), List.of("git", "status", "--short"));
    }

    @Tool(description = "查看工作区的 git 差异（git diff），可选限定路径")
    public String gitDiff(
            @ToolParam(description = "限定的相对路径，不传则全仓库", required = false) String path,
            ToolContext toolContext) {
        PathSandbox sandbox = WorkspaceContext.sandboxOf(toolContext);
        List<String> cmd = new ArrayList<>(List.of("git", "diff"));
        if (path != null && !path.isBlank()) {
            sandbox.resolve(path); // 越界校验
            cmd.add("--");
            cmd.add(path);
        }
        return run(sandbox, cmd);
    }

    @Tool(description = "提交工作区当前改动（git add -A && git commit）。危险操作，可能需要审批")
    public String gitCommit(
            @ToolParam(description = "提交信息") String message,
            ToolContext toolContext) {
        PathSandbox sandbox = WorkspaceContext.sandboxOf(toolContext);
        if (message == null || message.isBlank()) {
            return "提交信息不能为空";
        }
        String add = run(sandbox, List.of("git", "add", "-A"));
        if (add.startsWith("exit=") && !add.startsWith("exit=0")) {
            return "git add 失败：\n" + add;
        }
        return run(sandbox, List.of("git", "commit", "-m", message));
    }

    private String run(PathSandbox sandbox, List<String> command) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(sandbox.root().toFile());
        pb.redirectErrorStream(true);
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            return "启动 git 失败（未安装 git?）: " + e.getMessage();
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            process.getInputStream().transferTo(buffer);
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "git 命令超时";
            }
            String out = buffer.toString(StandardCharsets.UTF_8).stripTrailing();
            return "exit=" + process.exitValue() + "\n" + (out.isEmpty() ? "（无输出）" : out);
        } catch (IOException e) {
            return "读取 git 输出失败: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return "git 命令被中断";
        }
    }
}

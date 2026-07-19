package com.agentx.coding.tools;

import com.agentx.coding.sandbox.PathSandbox;
import com.agentx.tools.registry.AgentTool;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * CodeAgent shell 工具（设计文档 §4，危险级）。
 * 工作目录锁定工作区根；命令黑名单拦截明显破坏性/提权/远程执行；
 * 超时杀进程；输出行数/字节上限截断。
 */
@AgentTool(group = "coding")
public class ShellTools {

    private static final long TIMEOUT_SECONDS = 120;
    private static final int MAX_OUTPUT_LINES = 200;
    private static final int MAX_OUTPUT_BYTES = 20_000;

    /** 毁机级保护（任何模式下都拦，含 BYPASS）：系统级不可逆破坏。 */
    private static final List<Pattern> FATAL_BLACKLIST = List.of(
            Pattern.compile("\\brm\\s+-[a-z]*r[a-z]*f?\\s+/"),   // rm -rf /
            Pattern.compile(":\\(\\)\\s*\\{"),                     // fork bomb
            Pattern.compile("\\bmkfs\\b"),
            Pattern.compile(">\\s*/dev/(sd|disk|null/)"),
            Pattern.compile("\\bshutdown\\b|\\breboot\\b|\\bhalt\\b"),
            Pattern.compile("\\bdd\\b\\s+if="));

    /** 常规黑名单（BYPASS 模式解除）：提权/远程执行/系统目录写入。 */
    private static final List<Pattern> STRICT_BLACKLIST = List.of(
            Pattern.compile("\\bsudo\\b"),
            Pattern.compile("\\bsu\\b\\s"),
            Pattern.compile("\\b(curl|wget)\\b.*\\|\\s*(ba)?sh"), // curl … | sh
            Pattern.compile("\\bchmod\\s+-R\\s+777\\s+/"),
            Pattern.compile(">\\s*/etc/"));

    @Tool(description = "在工作区根目录执行 shell 命令（如构建、测试、git），返回 exit code 与输出。危险操作，可能需要审批")
    public String runShell(
            @ToolParam(description = "要执行的完整 shell 命令") String command,
            @ToolParam(required = false,
                    description = "一句话说明这条命令在做什么（中文，呈现给用户，如「运行测试」「安装依赖」）")
            String purpose,
            ToolContext toolContext) {
        PathSandbox sandbox = WorkspaceContext.sandboxOf(toolContext);
        if (command == null || command.isBlank()) {
            return "命令为空";
        }
        String lower = command.toLowerCase();
        for (Pattern p : FATAL_BLACKLIST) {
            if (p.matcher(lower).find()) {
                return "拒绝执行：命令命中安全黑名单（" + p.pattern() + "）";
            }
        }
        // BYPASS 模式解除常规黑名单（sudo/curl|sh 等），毁机级保护仍在上方兜底
        if (!WorkspaceContext.isBypass(toolContext)) {
            for (Pattern p : STRICT_BLACKLIST) {
                if (p.matcher(lower).find()) {
                    return "拒绝执行：命令命中安全黑名单（" + p.pattern() + "）";
                }
            }
        }
        return execute(sandbox, command);
    }

    private String execute(PathSandbox sandbox, String command) {
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
        pb.directory(sandbox.root().toFile());
        pb.redirectErrorStream(true);
        // 精简环境，避免继承敏感变量；PATH 保留基础
        pb.environment().keySet().removeIf(k -> k.startsWith("AGENTX_") || k.equals("AWS_SECRET_ACCESS_KEY"));
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            return "启动进程失败: " + e.getMessage();
        }
        StringBuilder out = new StringBuilder();
        int lineCount = 0;
        boolean truncated = false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lineCount < MAX_OUTPUT_LINES && out.length() < MAX_OUTPUT_BYTES) {
                    out.append(line).append('\n');
                    lineCount++;
                } else {
                    truncated = true;
                }
            }
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "命令超时（>" + TIMEOUT_SECONDS + "s）已终止。已捕获输出：\n" + out;
            }
            int exit = process.exitValue();
            String body = out.toString().stripTrailing();
            if (truncated) {
                body += "\n…（输出超限已截断）";
            }
            return "exit=" + exit + "\n" + body;
        } catch (IOException e) {
            return "读取输出失败: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return "命令被中断";
        }
    }
}

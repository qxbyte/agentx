package com.agentx.coding.tools;

import com.agentx.coding.sandbox.PathSandbox;
import com.agentx.tools.registry.AgentTool;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * CodeAgent 只读探索工具（设计文档 §4，Plan/Ask/Auto 三档均可用）。
 * 全部经 {@link WorkspaceContext} 拿沙箱，路径越界由沙箱拦截。
 */
@AgentTool(group = "coding")
public class WorkspaceReadTools {

    /** 遍历时忽略的目录（噪音大、无检索价值）。 */
    private static final Set<String> IGNORED_DIRS =
            Set.of(".git", "node_modules", "target", "build", "dist", ".idea", ".gradle");
    private static final int MAX_FILE_BYTES = 200_000;
    private static final int MAX_LIST_ENTRIES = 500;
    private static final int MAX_GREP_HITS = 100;

    @Tool(description = "列出工作区内某个目录的文件树（相对路径），忽略 .git/node_modules 等噪音目录")
    public String listDir(
            @ToolParam(description = "相对工作区根的目录路径，根目录传空串或 .") String path,
            @ToolParam(description = "递归深度，1 表示只列当前层，建议 1-3", required = false) Integer depth,
            @ToolParam(description = "用几个字说明这一步意图，如：查看项目结构", required = false) String purpose,
            ToolContext toolContext) {
        PathSandbox sandbox = WorkspaceContext.readSandboxOf(toolContext);
        Path dir = sandbox.resolve(path == null || path.isBlank() ? "." : path);
        if (!Files.isDirectory(dir)) {
            return "不是目录: " + path;
        }
        int maxDepth = depth == null ? 2 : Math.max(1, Math.min(depth, 6));
        List<String> lines = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir, maxDepth)) {
            walk.filter(p -> !p.equals(dir))
                    .filter(p -> IGNORED_DIRS.stream().noneMatch(ig -> p.toString().contains("/" + ig)))
                    .limit(MAX_LIST_ENTRIES)
                    .forEach(p -> lines.add((Files.isDirectory(p) ? "d " : "f ") + sandbox.relativize(p)));
        } catch (IOException e) {
            return "读取目录失败: " + e.getMessage();
        }
        return lines.isEmpty() ? "（空目录）" : String.join("\n", lines);
    }

    @Tool(description = "读取工作区内某个文件的内容，可按行区间切片；大文件必须切片读取")
    public String readFile(
            @ToolParam(description = "相对工作区根的文件路径") String path,
            @ToolParam(description = "起始行（1 起，含），不传从头", required = false) Integer fromLine,
            @ToolParam(description = "结束行（含），不传到尾", required = false) Integer toLine,
            @ToolParam(description = "用几个字说明这一步意图，如：查看项目结构", required = false) String purpose,
            ToolContext toolContext) {
        PathSandbox sandbox = WorkspaceContext.readSandboxOf(toolContext);
        Path file = sandbox.resolve(path);
        if (!Files.isRegularFile(file)) {
            return "文件不存在: " + path;
        }
        try {
            if (fromLine == null && toLine == null && Files.size(file) > MAX_FILE_BYTES) {
                return "文件过大（>" + MAX_FILE_BYTES + " 字节），请用 fromLine/toLine 切片读取";
            }
            List<String> all = Files.readAllLines(file);
            int from = fromLine == null ? 1 : Math.max(1, fromLine);
            int to = toLine == null ? all.size() : Math.min(all.size(), toLine);
            if (from > all.size()) {
                return "起始行超出文件总行数 " + all.size();
            }
            StringBuilder sb = new StringBuilder();
            for (int i = from; i <= to; i++) {
                sb.append(i).append('\t').append(all.get(i - 1)).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            return "读取文件失败: " + e.getMessage();
        }
    }

    @Tool(description = "在工作区内按正则搜索文件内容，返回命中的 文件:行号:内容")
    public String grepFiles(
            @ToolParam(description = "正则表达式（Java 语法）") String pattern,
            @ToolParam(description = "文件名后缀过滤，如 .java；不传搜全部文本文件", required = false) String suffix,
            @ToolParam(description = "用几个字说明这一步意图，如：查看项目结构", required = false) String purpose,
            ToolContext toolContext) {
        PathSandbox sandbox = WorkspaceContext.readSandboxOf(toolContext);
        Pattern regex;
        try {
            regex = Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            return "正则语法错误: " + e.getMessage();
        }
        List<String> hits = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sandbox.root())) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> IGNORED_DIRS.stream().noneMatch(ig -> p.toString().contains("/" + ig)))
                    .filter(p -> suffix == null || suffix.isBlank() || p.toString().endsWith(suffix))
                    .forEach(p -> collectHits(p, regex, sandbox, hits));
        } catch (IOException e) {
            return "搜索失败: " + e.getMessage();
        }
        if (hits.isEmpty()) {
            return "无命中";
        }
        boolean truncated = hits.size() > MAX_GREP_HITS;
        String body = String.join("\n", hits.subList(0, Math.min(hits.size(), MAX_GREP_HITS)));
        return truncated ? body + "\n…（命中过多，已截断至 " + MAX_GREP_HITS + " 条）" : body;
    }

    @Tool(description = "在工作区内按 glob 查找文件名，返回相对路径列表")
    public String findFiles(
            @ToolParam(description = "glob，如 **/*.java 或 *Controller.java") String glob,
            @ToolParam(description = "用几个字说明这一步意图，如：查看项目结构", required = false) String purpose,
            ToolContext toolContext) {
        PathSandbox sandbox = WorkspaceContext.readSandboxOf(toolContext);
        var matcher = sandbox.root().getFileSystem().getPathMatcher("glob:" + glob);
        List<String> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sandbox.root())) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> IGNORED_DIRS.stream().noneMatch(ig -> p.toString().contains("/" + ig)))
                    .filter(p -> matcher.matches(sandbox.root().relativize(p)) || matcher.matches(p.getFileName()))
                    .limit(MAX_LIST_ENTRIES)
                    .forEach(p -> found.add(sandbox.relativize(p)));
        } catch (IOException e) {
            return "查找失败: " + e.getMessage();
        }
        return found.isEmpty() ? "无匹配文件" : String.join("\n", found);
    }

    private void collectHits(Path file, Pattern regex, PathSandbox sandbox, List<String> hits) {
        if (hits.size() >= MAX_GREP_HITS + 1) {
            return;
        }
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                return;
            }
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size() && hits.size() <= MAX_GREP_HITS; i++) {
                if (regex.matcher(lines.get(i)).find()) {
                    String text = lines.get(i);
                    hits.add(sandbox.relativize(file) + ":" + (i + 1) + ":"
                            + (text.length() > 200 ? text.substring(0, 200) + "…" : text));
                }
            }
        } catch (IOException | java.io.UncheckedIOException ignored) {
            // 二进制/不可读文件跳过
        }
    }
}

package com.agentx.coding.tools;

import com.agentx.coding.patch.UnifiedDiff;
import com.agentx.coding.sandbox.PathSandbox;
import com.agentx.tools.registry.AgentTool;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CodeAgent 变更工具（设计文档 §4，危险级——ASK 审批 / PLAN 不可见）。
 */
@AgentTool(group = "coding")
public class WorkspaceEditTools {

    @Tool(description = "覆盖或新建工作区内的文件（写入全部内容）。危险操作，可能需要审批")
    public String writeFile(
            @ToolParam(description = "相对工作区根的文件路径") String path,
            @ToolParam(description = "文件完整内容") String content,
            ToolContext toolContext) {
        PathSandbox sandbox = WorkspaceContext.sandboxOf(toolContext);
        Path file = sandbox.resolve(path);
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, content == null ? "" : content);
            return "已写入 " + path + "（" + (content == null ? 0 : content.length()) + " 字符）";
        } catch (IOException e) {
            return "写入失败: " + e.getMessage();
        }
    }

    @Tool(description = "对工作区内文件应用 unified diff 补丁（精确编辑，支持多 hunk）。危险操作，可能需要审批")
    public String applyPatch(
            @ToolParam(description = "unified diff 文本（--- a/ +++ b/ @@ 格式）") String unifiedDiff,
            ToolContext toolContext) {
        PathSandbox sandbox = WorkspaceContext.sandboxOf(toolContext);
        List<UnifiedDiff.FilePatch> patches;
        try {
            patches = UnifiedDiff.parse(unifiedDiff);
        } catch (IllegalArgumentException e) {
            return "补丁解析失败: " + e.getMessage();
        }
        if (patches.isEmpty()) {
            return "补丁为空";
        }
        StringBuilder result = new StringBuilder();
        for (UnifiedDiff.FilePatch patch : patches) {
            Path file = sandbox.resolve(patch.path());
            try {
                List<String> original = Files.exists(file) ? Files.readAllLines(file) : List.of();
                List<String> patched = UnifiedDiff.apply(original, patch);
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }
                Files.write(file, patched);
                result.append("已修改 ").append(patch.path())
                        .append("（+").append(patch.added()).append(" -").append(patch.removed())
                        .append("）\n");
            } catch (IOException e) {
                return "写入 " + patch.path() + " 失败: " + e.getMessage();
            } catch (IllegalStateException e) {
                return "补丁应用到 " + patch.path() + " 失败（上下文不匹配）: " + e.getMessage();
            }
        }
        return result.toString().strip();
    }
}

package com.agentx.coding.runtime;

import com.agentx.coding.tools.WorkspaceContext;
import com.agentx.infra.ai.stream.ToolPreviewProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

/**
 * 编码工具的帧富化实现（{@link ToolPreviewProvider}）：把 kind/preview 注入 tool-call/tool-result 帧。
 * 逻辑与 {@link ApprovalGate} 共用 {@link CodingToolPreviews}，仅 CodingStreamCustomizer 注入使用，
 * 普通对话与 agent 会话不受影响。
 */
@Component
@RequiredArgsConstructor
public class CodingToolPreviewProvider implements ToolPreviewProvider {

    /** 单侧（旧文件或新内容）超过该字节数不算 diff：防超大文件拖慢帧下发。 */
    private static final int MAX_DIFF_SOURCE_BYTES = 100_000;
    private static final int CONTEXT_LINES = 3;

    private final ObjectMapper objectMapper;

    @Override
    public String kindOf(String toolName) {
        return CodingToolPreviews.kindOf(toolName);
    }

    @Override
    public Map<String, Object> previewOf(String toolName, String argsJson) {
        return CodingToolPreviews.previewOf(toolName, argsJson, objectMapper);
    }

    @Override
    public Map<String, Object> previewOf(String toolName, String argsJson, ToolContext toolContext) {
        Map<String, Object> base = previewOf(toolName, argsJson);
        if (!"writeFile".equals(toolName) || base == null || toolContext == null) {
            return base;
        }
        try {
            String path = String.valueOf(base.getOrDefault("path", ""));
            String content = String.valueOf(base.getOrDefault("content", ""));
            if (path.isBlank() || content.length() > MAX_DIFF_SOURCE_BYTES) {
                return base;
            }
            java.nio.file.Path file = WorkspaceContext.sandboxOf(toolContext).resolve(path);
            List<String> oldLines;
            if (java.nio.file.Files.isRegularFile(file)) {
                if (java.nio.file.Files.size(file) > MAX_DIFF_SOURCE_BYTES) {
                    return base;
                }
                oldLines = java.nio.file.Files.readAllLines(file);
            } else {
                oldLines = List.of();
            }
            List<String> newLines = content.lines().toList();
            var patch = com.github.difflib.DiffUtils.diff(oldLines, newLines);
            List<String> unified = com.github.difflib.UnifiedDiffUtils.generateUnifiedDiff(
                    "a/" + path, "b/" + path, oldLines, patch, CONTEXT_LINES);
            Map<String, Object> preview = new java.util.LinkedHashMap<>();
            preview.put("path", path);
            preview.put("diff", String.join("\n", unified));
            return preview;
        } catch (Exception e) {
            // 沙箱越界/IO 异常等：预览是锦上添花，回退基础版不阻塞工具执行
            return base;
        }
    }
}

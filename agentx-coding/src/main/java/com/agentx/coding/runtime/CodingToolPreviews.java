package com.agentx.coding.runtime;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 编码工具的展示元数据（kind + 结构化预览）——审批网关与 SSE 帧富化共用（DRY，设计文档 §7）。
 * 纯函数：由工具名与入参 JSON 推导，前端据此分发到 DiffView / ShellOutput / ToolResultCard。
 */
public final class CodingToolPreviews {

    private CodingToolPreviews() {}

    /** 工具展示类型；非编码工具返回 null（帧退化为纯文本）。 */
    public static String kindOf(String toolName) {
        return switch (toolName) {
            case "applyPatch" -> "patch";
            case "runShell" -> "shell";
            case "gitCommit" -> "commit";
            case "writeFile" -> "write";
            case "readFile" -> "read";
            case "grepFiles" -> "grep";
            case "findFiles" -> "find";
            case "listDir" -> "list";
            case "gitStatus", "gitDiff" -> "git";
            default -> null;
        };
    }

    /** 从入参构建 tool-call / 审批预览；无结构化预览时返回 null。 */
    public static Map<String, Object> previewOf(String toolName, String argsJson, ObjectMapper om) {
        Map<String, Object> args = parseArgs(argsJson, om);
        Map<String, Object> preview = new LinkedHashMap<>();
        switch (toolName) {
            case "applyPatch" -> preview.put("diff", str(args.get("unifiedDiff")));
            case "runShell" -> preview.put("command", str(args.get("command")));
            case "gitCommit" -> preview.put("message", str(args.get("message")));
            case "writeFile" -> {
                preview.put("path", str(args.get("path")));
                preview.put("content", str(args.get("content")));
            }
            case "readFile", "grepFiles", "findFiles", "listDir" -> {
                Object path = args.get("path");
                Object query = args.get("query");
                if (path != null) preview.put("path", str(path));
                if (query != null) preview.put("query", str(query));
            }
            default -> {
                return null;
            }
        }
        return preview;
    }

    private static Map<String, Object> parseArgs(String argsJson, ObjectMapper om) {
        if (argsJson == null || argsJson.isBlank()) {
            return Map.of();
        }
        try {
            return om.readValue(argsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }
}

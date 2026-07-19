package com.agentx.tools.builtin.files;

import com.agentx.common.util.UuidV7;
import com.agentx.tools.files.FileStorage;
import com.agentx.tools.files.GeneratedFile;
import com.agentx.tools.files.GeneratedFileRepository;
import com.agentx.tools.files.render.DocRenderService;
import com.agentx.tools.files.render.XlsxRenderer;
import com.agentx.tools.registry.AgentTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * 文件生成工具：结构化渲染落盘产出文档（不经代码沙箱）。
 * 交付三段式：渲染字节 → 注册 generated_file 实体（受管存储）→ 结果只回引用（fileId），
 * 前端识别工具名渲染文件卡片，经 /api/v1/files/{id}/download 鉴权下载。
 * 编码会话可用 savePath 直接写入项目工作区（工作区写边界：越界忽略并说明）。
 */
@Slf4j
@AgentTool(group = "files")
@RequiredArgsConstructor
public class FileTools {

    public static final String DOC_TOOL = "generateDocument";
    public static final String SHEET_TOOL = "generateSpreadsheet";

    /** 与 CodingStreamCustomizer 注入 toolContext 的键一致（tools 模块不依赖 coding，用字面量） */
    private static final String WORKSPACE_ROOT_KEY = "codingWorkspaceRoot";
    private static final int MAX_CONTENT_CHARS = 200_000;

    private final DocRenderService renderService;
    private final FileStorage storage;
    private final GeneratedFileRepository repository;

    @Tool(name = DOC_TOOL, description = """
            生成一个文档文件（md / docx / pdf / pptx）供用户下载，仅在用户明确要求生成文件/文档/PPT/报告时使用。
            content 一律写 Markdown：docx/pdf 按标题层级排版；pptx 按标题分页——
            `# 标题` 是封面页（其后紧跟的普通行为副标题），每个 `## 标题` 是一页，页内用 `-` 列表呈现要点（可两级缩进），
            单页要点不超过 6 条，PPT 全文不写大段落。
            绑定项目的编码会话中可传 savePath（工作区相对路径，如 docs/report.docx）把文件直接写入项目目录。""")
    public String generateDocument(
            @ToolParam(description = "目标格式：md | docx | pdf | pptx") String format,
            @ToolParam(description = "文件名（不含路径，可不带扩展名）") String filename,
            @ToolParam(description = "文件内容（Markdown）") String content,
            @ToolParam(required = false, description = "编码会话专用：写入项目的工作区相对路径") String savePath,
            ToolContext toolContext) {
        String fmt = format == null ? "" : format.strip().toLowerCase();
        if (!DocRenderService.DOCUMENT_FORMATS.contains(fmt)) {
            return "不支持的格式: " + format + "，可选 md / docx / pdf / pptx（表格数据请用 " + SHEET_TOOL + "）";
        }
        if (content != null && content.length() > MAX_CONTENT_CHARS) {
            return "内容过长（>" + MAX_CONTENT_CHARS + " 字符），请精简或拆分为多个文件";
        }
        try {
            return deliver(renderService.renderDocument(fmt, content), fmt, filename, savePath, toolContext);
        } catch (Exception e) {
            log.warn("文档生成失败 format={}: {}", fmt, e.getMessage());
            return "文件生成失败: " + e.getMessage();
        }
    }

    @Tool(name = SHEET_TOOL, description = """
            生成一个 Excel 表格文件（xlsx）供用户下载，仅在用户明确要求生成表格/Excel 文件时使用。
            每个 sheet 传表名、表头数组、数据行数组（全部为字符串，数字会自动识别为数值单元格）。
            绑定项目的编码会话中可传 savePath（工作区相对路径）把文件直接写入项目目录。""")
    public String generateSpreadsheet(
            @ToolParam(description = "文件名（不含路径，可不带扩展名）") String filename,
            @ToolParam(description = "工作表列表（覆盖式全量）") List<XlsxRenderer.Sheet> sheets,
            @ToolParam(required = false, description = "编码会话专用：写入项目的工作区相对路径") String savePath,
            ToolContext toolContext) {
        try {
            return deliver(renderService.renderSpreadsheet(sheets), "xlsx", filename, savePath, toolContext);
        } catch (Exception e) {
            log.warn("表格生成失败: {}", e.getMessage());
            return "文件生成失败: " + e.getMessage();
        }
    }

    private String deliver(byte[] bytes, String ext, String filename, String savePath,
                           ToolContext toolContext) {
        UUID userId = contextUuid(toolContext, "userId");
        UUID conversationId = contextUuid(toolContext, "conversationId");
        if (userId == null) {
            return "会话上下文缺失（无 userId），无法登记生成文件";
        }
        String safeName = sanitizeFilename(filename, ext);

        UUID fileId = UuidV7.next();
        Path stored = storage.store(conversationId, fileId, ext, bytes);

        // 编码会话指定位置：规范化后必须落在工作区根内，.git 受保护；越界只提示不失败
        String savedPath = null;
        String saveNote = "";
        Object workspaceRoot = toolContext == null ? null : toolContext.getContext().get(WORKSPACE_ROOT_KEY);
        if (savePath != null && !savePath.isBlank()) {
            if (workspaceRoot instanceof String rootStr && !rootStr.isBlank()) {
                try {
                    savedPath = writeToWorkspace(Path.of(rootStr), savePath, safeName, bytes);
                } catch (IllegalArgumentException e) {
                    saveNote = "；savePath 未生效（" + e.getMessage() + "），文件仍可通过下载获取";
                }
            } else {
                saveNote = "；savePath 仅编码会话可用，文件仍可通过下载获取";
            }
        }

        GeneratedFile record = new GeneratedFile();
        record.setId(fileId);
        record.setUserId(userId);
        record.setConversationId(conversationId);
        record.setFilename(safeName);
        record.setFormat(ext);
        record.setSizeBytes(bytes.length);
        record.setPath(stored.toString());
        record.setSavedPath(savedPath);
        repository.save(record);

        // 结构化 JSON 供前端文件卡片解析；模型据此向用户播报（不要虚构下载链接）
        return """
                {"fileId":"%s","filename":"%s","format":"%s","sizeBytes":%d,"savedPath":%s}\
                """.formatted(fileId, escapeJson(safeName), ext, bytes.length,
                savedPath == null ? "null" : "\"" + escapeJson(savedPath) + "\"") + saveNote;
    }

    /** 工作区内落盘：路径规范化 + 边界校验，返回工作区相对路径。 */
    private String writeToWorkspace(Path root, String savePath, String fallbackName, byte[] bytes) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        String cleaned = savePath.strip();
        // 传目录时补上文件名
        Path target = normalizedRoot.resolve(cleaned).normalize();
        if (!target.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("路径越出工作区");
        }
        for (Path part : normalizedRoot.relativize(target)) {
            if (part.toString().equals(".git")) {
                throw new IllegalArgumentException(".git 目录受保护");
            }
        }
        try {
            if (cleaned.endsWith("/") || Files.isDirectory(target)) {
                target = target.resolve(fallbackName);
            }
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            return normalizedRoot.relativize(target).toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("写入失败: " + e.getMessage());
        }
    }

    /** 文件名净化：剥离路径分隔符与控制字符，确保扩展名与格式一致。 */
    private String sanitizeFilename(String filename, String ext) {
        String base = filename == null || filename.isBlank() ? "untitled" : filename.strip();
        base = base.replaceAll("[/\\\\:*?\"<>|\\p{Cntrl}]", "_");
        String suffix = "." + ext;
        if (base.toLowerCase().endsWith(suffix)) {
            base = base.substring(0, base.length() - suffix.length());
        }
        if (base.length() > 120) {
            base = base.substring(0, 120);
        }
        return base + suffix;
    }

    private UUID contextUuid(ToolContext toolContext, String key) {
        Object value = toolContext == null ? null : toolContext.getContext().get(key);
        try {
            return value == null ? null : UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

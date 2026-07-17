package com.agentx.chat.service;

import com.agentx.chat.domain.ChatAttachment;
import com.agentx.chat.domain.ChatAttachmentRepository;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.common.util.UuidV7;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 会话附件：上传即解析（Tika → 文本，保头截断并显式标注），发送时绑定消息，
 * 注入采用 Anthropic 官方推荐的 <documents> XML 包装（含 source 溯源与注入边界）。
 */
@Slf4j
@Service
public class AttachmentService {

    /** 单文件字节硬限 */
    private static final long MAX_FILE_BYTES = 30L * 1024 * 1024;
    /** 单文件解析文本软限（保头截断） */
    private static final int MAX_CHARS_PER_FILE = 40_000;
    /** 单条消息注入总量：超出的文件降级为仅文件名条目 */
    private static final int MAX_TOTAL_CHARS = 150_000;
    /** 单请求文件数上限 */
    public static final int MAX_FILES_PER_REQUEST = 50;

    /** 图片扩展名（走视觉模型 Media 通道，不做文本解析）；GIF 仅首帧被模型识别 */
    public static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");
    /** 单图字节硬限（对齐 Claude API 的 10MB；前端已按 1568px 降采样） */
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    /** 扩展名白名单：常用文档 + 文本/代码；可执行与压缩包拒收 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "csv",
            "md", "markdown", "txt", "json", "xml", "yaml", "yml", "html", "htm",
            "java", "kt", "ts", "tsx", "js", "jsx", "py", "go", "rs", "rb", "c", "h",
            "cpp", "hpp", "cs", "swift", "css", "scss", "sql", "sh", "bash", "zsh",
            "properties", "toml", "ini", "conf", "gradle", "proto", "vue", "log");
    /** 无扩展名但常见的纯文本文件名 */
    private static final Set<String> ALLOWED_BARE_NAMES = Set.of(
            "dockerfile", "makefile", "license", "readme", "gemfile", "procfile");

    private final ChatAttachmentRepository repository;
    private final Tika tika = new Tika();
    private final Path storageRoot;

    public AttachmentService(ChatAttachmentRepository repository,
                             @Value("${agentx.attachments.storage-dir:${user.home}/.agentx/attachments}") String dir) {
        this.repository = repository;
        this.storageRoot = Path.of(dir);
    }

    /** 上传结果：成功项带 id，失败项带 error（单文件失败不阻塞其余）。 */
    public record UploadResult(UUID id, String filename, String relPath, String kind, long sizeBytes,
                               int charCount, boolean truncated, String error) {
        static UploadResult failure(String filename, String relPath, long size, String error) {
            return new UploadResult(null, filename, relPath, "text", size, 0, false, error);
        }
    }

    @Transactional
    public List<UploadResult> upload(UUID userId, List<MultipartFile> files, List<String> relPaths) {
        if (files == null || files.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "未选择文件");
        }
        if (files.size() > MAX_FILES_PER_REQUEST) {
            throw new BizException(ErrorCode.BAD_REQUEST, "单次最多上传 " + MAX_FILES_PER_REQUEST + " 个文件");
        }
        List<UploadResult> results = new java.util.ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String relPath = relPaths != null && i < relPaths.size() && !relPaths.get(i).isBlank()
                    ? relPaths.get(i) : null;
            results.add(uploadOne(userId, file, relPath));
        }
        return results;
    }

    private UploadResult uploadOne(UUID userId, MultipartFile file, String relPath) {
        String filename = Path.of(file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename())
                .getFileName().toString();
        String ext = extensionOf(filename);
        boolean image = IMAGE_EXTENSIONS.contains(ext);
        if (image && file.getSize() > MAX_IMAGE_BYTES) {
            return UploadResult.failure(filename, relPath, file.getSize(), "图片超过 10MB 上限");
        }
        if (!image && file.getSize() > MAX_FILE_BYTES) {
            return UploadResult.failure(filename, relPath, file.getSize(), "超过单文件 30MB 上限");
        }
        if (!image && !ALLOWED_EXTENSIONS.contains(ext)
                && !ALLOWED_BARE_NAMES.contains(filename.toLowerCase())) {
            return UploadResult.failure(filename, relPath, file.getSize(), "不支持的文件类型: " + filename);
        }
        try {
            UUID id = UuidV7.next();
            Path dir = storageRoot.resolve(userId.toString());
            Files.createDirectories(dir);
            Path stored = dir.resolve(ext.isEmpty() ? id.toString() : id + "." + ext);
            file.transferTo(stored);

            // 图片不做文本解析：原图经 Spring AI Media 通道交给视觉模型
            String text = image ? "" : parseText(stored);
            boolean truncated = text.length() > MAX_CHARS_PER_FILE;
            if (truncated) {
                text = text.substring(0, MAX_CHARS_PER_FILE);
            }

            ChatAttachment attachment = new ChatAttachment();
            attachment.setId(id);
            attachment.setUserId(userId);
            attachment.setFilename(filename);
            attachment.setKind(image ? "image" : "text");
            attachment.setRelPath(relPath);
            attachment.setSizeBytes(file.getSize());
            attachment.setCharCount(text.length());
            attachment.setTruncated(truncated);
            attachment.setStoragePath(stored.toString());
            attachment.setParsedText(text);
            repository.save(attachment);
            return new UploadResult(id, filename, relPath, attachment.getKind(),
                    file.getSize(), text.length(), truncated, null);
        } catch (Exception e) {
            log.warn("附件解析失败 {}: {}", filename, e.getMessage());
            return UploadResult.failure(filename, relPath, file.getSize(), "解析失败: " + e.getMessage());
        }
    }

    private String parseText(Path stored) throws Exception {
        // Tika 统一解析（含纯文本编码探测）；限长避免超大文档撑爆内存
        tika.setMaxStringLength(MAX_CHARS_PER_FILE + 1);
        String text = tika.parseToString(stored);
        return text == null ? "" : text.strip();
    }

    /** 发送时绑定：校验归属且未被其他消息占用，回填会话与消息。 */
    @Transactional
    public List<ChatAttachment> bindToMessage(List<UUID> ids, UUID userId,
                                              UUID conversationId, UUID messageId) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<ChatAttachment> attachments = repository.findByIdInAndUserIdAndMessageIdIsNull(ids, userId);
        if (attachments.size() != ids.size()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "存在无效或已使用的附件");
        }
        for (ChatAttachment a : attachments) {
            a.setConversationId(conversationId);
            a.setMessageId(messageId);
        }
        repository.saveAll(attachments);
        // 保持用户选择顺序
        attachments.sort(java.util.Comparator.comparingInt(a -> ids.indexOf(a.getId())));
        return attachments;
    }

    /**
     * 注入包装：<documents> XML 前置在用户输入之前（Anthropic 官方推荐结构）。
     * 超出单条消息总量的文件降级为仅文件名条目并显式标注——绝不静默丢内容。
     */
    public String wrapForPrompt(List<ChatAttachment> allAttachments, String userContent) {
        // 图片不进 <documents>——原图经 Media 通道直达视觉模型（见 ChatStreamService）
        List<ChatAttachment> attachments = allAttachments.stream()
                .filter(a -> !"image".equals(a.getKind())).toList();
        if (attachments.isEmpty()) {
            return userContent;
        }
        StringBuilder sb = new StringBuilder("<documents>\n");
        int budget = MAX_TOTAL_CHARS;
        int index = 1;
        for (ChatAttachment a : attachments) {
            String source = a.getRelPath() != null ? a.getRelPath() : a.getFilename();
            sb.append("<document index=\"").append(index++).append("\">\n")
                    .append("<source>").append(escapeXml(source)).append("</source>\n");
            if (a.getParsedText().length() <= budget) {
                sb.append("<document_contents>\n").append(a.getParsedText()).append("\n</document_contents>\n");
                budget -= a.getParsedText().length();
                if (a.isTruncated()) {
                    sb.append("<note>文档已按单文件上限截断：此处仅含全文开头的 ")
                            .append(a.getCharCount()).append(" 字符</note>\n");
                }
            } else {
                sb.append("<note>因单条消息注入总量限制未注入内容（文件共 ")
                        .append(a.getCharCount()).append(" 字符），仅供知晓该文件存在</note>\n");
            }
            sb.append("</document>\n");
        }
        sb.append("</documents>\n\n").append(userContent);
        return sb.toString();
    }

    /** 附件元数据 JSON（落 chat_message.attachments，供历史气泡渲染芯片）。 */
    public String metadataJson(List<ChatAttachment> attachments) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < attachments.size(); i++) {
            ChatAttachment a = attachments.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":\"").append(a.getId())
                    .append("\",\"filename\":\"").append(a.getFilename().replace("\\", "\\\\").replace("\"", "\\\""))
                    .append("\",\"kind\":\"").append(a.getKind())
                    .append("\",\"sizeBytes\":").append(a.getSizeBytes()).append('}');
        }
        return sb.append(']').toString();
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }
}

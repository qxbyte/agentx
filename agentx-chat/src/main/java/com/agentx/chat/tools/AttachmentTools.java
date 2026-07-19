package com.agentx.chat.tools;

import com.agentx.chat.domain.ChatAttachment;
import com.agentx.chat.domain.ChatAttachmentRepository;
import com.agentx.tools.registry.AgentTool;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.util.UUID;

/**
 * 附件按需重读工具：附件全文只在上传当轮注入，历史轮次的记忆里是占位符——
 * 模型需要引用细节时经本工具取回（落盘存引用、按需读回）。归属校验：仅本人的附件可读。
 */
@AgentTool(group = "attachment")
@RequiredArgsConstructor
public class AttachmentTools {

    public static final String READ_TOOL = "readAttachment";

    /** 单次返回上限：超长文档分页读取（offset 翻页）。 */
    private static final int PAGE_CHARS = 30_000;

    private final ChatAttachmentRepository repository;

    @Tool(description = """
            重新读取一个此前上传的附件全文。历史消息里的附件占位符（含 attachmentId）\
            指向的内容可经本工具取回。超长文档分页返回，用 offset 继续读取。""")
    public String readAttachment(
            @ToolParam(description = "附件 id（历史消息占位符中给出）") String attachmentId,
            @ToolParam(required = false, description = "起始字符偏移（默认 0，翻页时用上次返回提示的值）")
            Integer offset,
            ToolContext toolContext) {
        UUID userId = userIdOf(toolContext);
        UUID id;
        try {
            id = UUID.fromString(attachmentId.strip());
        } catch (IllegalArgumentException e) {
            return "附件 id 格式不正确：" + attachmentId;
        }
        ChatAttachment attachment = repository.findByIdAndUserId(id, userId).orElse(null);
        if (attachment == null) {
            return "附件不存在或无权访问：" + attachmentId;
        }
        String text = attachment.getParsedText();
        if (text == null || text.isBlank()) {
            return "该附件（" + attachment.getFilename() + "）没有可读取的文本内容";
        }
        int from = offset == null ? 0 : Math.max(0, offset);
        if (from >= text.length()) {
            return "offset 超出范围：全文共 " + text.length() + " 字符";
        }
        int to = Math.min(text.length(), from + PAGE_CHARS);
        String page = text.substring(from, to);
        String header = "【" + attachment.getFilename() + " 第 " + from + "-" + to
                + " 字符 / 共 " + text.length() + " 字符】\n";
        String footer = to < text.length()
                ? "\n【未完：继续读取请传 offset=" + to + "】" : "";
        return header + page + footer;
    }

    private static UUID userIdOf(ToolContext toolContext) {
        Object userId = toolContext.getContext().get("userId");
        if (userId == null) {
            throw new IllegalStateException("工具上下文缺少 userId");
        }
        return UUID.fromString(userId.toString());
    }
}

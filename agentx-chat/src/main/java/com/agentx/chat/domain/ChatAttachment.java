package com.agentx.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/**
 * 会话附件（输入框上传的文件 / 文件夹展开文件）。
 * 上传即解析（Tika → 文本，保头截断），发送时绑定消息并以
 * &lt;documents&gt; XML 包装注入本轮 user prompt（设计文档：对话附件上传与解析）。
 */
@Getter
@Setter
@Entity
@Table(name = "chat_attachment")
public class ChatAttachment {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** 发送时回填；未发送的孤儿附件可定期清理 */
    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(nullable = false)
    private String filename;

    /** text：解析文本注入 <documents>；image：原图经 Spring AI Media 通道走视觉模型 */
    @Column(nullable = false, length = 16)
    private String kind = "text";

    /** 文件夹上传时的相对路径（如 src/utils/date.ts），单文件为 null */
    @Column(name = "rel_path")
    private String relPath;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    @Column(nullable = false)
    private boolean truncated;

    /** 原文件落盘路径 */
    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    /** 解析 + 截断后的文本（入库避免二次解析） */
    @Column(name = "parsed_text", nullable = false)
    private String parsedText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

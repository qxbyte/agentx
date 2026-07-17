package com.agentx.tools.files;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/**
 * 模型生成的文件记录（generateDocument / generateSpreadsheet 产物）。
 * 学 OpenAI 三段式交付：文件注册为实体 → 消息只携带引用（fileId）→
 * 前端经 /api/v1/files/{id}/download 鉴权换字节流。持久化，随会话删除级联清理。
 */
@Getter
@Setter
@Entity
@Table(name = "generated_file")
public class GeneratedFile {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** 归属会话；工具上下文缺失时可为空（仅限管理员手工清理场景） */
    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(nullable = false)
    private String filename;

    /** md / docx / pdf / pptx / xlsx */
    @Column(nullable = false, length = 16)
    private String format;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** 受管存储的绝对路径 */
    @Column(nullable = false)
    private String path;

    /** 编码会话指定位置生成时的工作区相对路径（仅展示用途） */
    @Column(name = "saved_path")
    private String savedPath;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

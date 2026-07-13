package com.agentx.coding.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "coding_workspace")
public class CodingWorkspace {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    /** 服务器上的绝对目录（待修仓库根）。 */
    @Column(name = "root_path", nullable = false)
    private String rootPath;

    /** 可空：绑定知识库以在编码会话中检索规范/背景。 */
    @Column(name = "kb_id")
    private UUID kbId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

package com.agentx.chat.service.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;

/**
 * md 文件长期记忆（AGENTX.md）：
 * <ul>
 *   <li>用户级：{@code ~/.agentx/AGENTX.md}——跨会话的用户偏好与事实；</li>
 *   <li>项目级：{@code <工作区根>/AGENTX.md}——项目约定，随代码库走、可进版本管理。</li>
 * </ul>
 * 读取注入 system 上下文（内容稳定则逐轮字节一致，KV-cache 友好）；
 * 写入走 saveMemory 工具追加条目。文件不存在视为无记忆，IO 失败只降级不报错——
 * 记忆是增强项，绝不阻塞主链路。
 */
@Slf4j
@Service
public class MemoryFileService {

    public static final String MEMORY_FILENAME = "AGENTX.md";
    /** 单文件注入上限：超出截断并标注（防手写超长 md 挤占上下文）。 */
    private static final int MAX_INJECT_CHARS = 12_000;

    /** 用户级记忆全文；不存在/不可读返回空串。 */
    public String readUserMemory() {
        return readCapped(userMemoryPath());
    }

    /** 项目级记忆全文；工作区未配置/文件不存在返回空串。 */
    public String readWorkspaceMemory(String workspaceRoot) {
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            return "";
        }
        return readCapped(Path.of(workspaceRoot, MEMORY_FILENAME));
    }

    /** 追加一条用户级记忆（带日期前缀，便于人工回看与清理）。 */
    public void appendUserMemory(String content) {
        append(userMemoryPath(), content);
    }

    /** 追加一条项目级记忆。 */
    public void appendWorkspaceMemory(String workspaceRoot, String content) {
        append(Path.of(workspaceRoot, MEMORY_FILENAME), content);
    }

    private Path userMemoryPath() {
        return Path.of(System.getProperty("user.home"), ".agentx", MEMORY_FILENAME);
    }

    private String readCapped(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return "";
            }
            String content = Files.readString(path, StandardCharsets.UTF_8).strip();
            if (content.length() > MAX_INJECT_CHARS) {
                return content.substring(0, MAX_INJECT_CHARS) + "\n…（记忆文件超长已截断，完整内容见 " + path + "）";
            }
            return content;
        } catch (IOException e) {
            log.warn("记忆文件读取失败（按无记忆处理）{}: {}", path, e.getMessage());
            return "";
        }
    }

    private void append(Path path, String content) {
        String entry = "- " + LocalDate.now() + " " + content.strip().replace("\n", "\n  ") + "\n";
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                Files.writeString(path, "# AgentX 记忆\n\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE);
            }
            Files.writeString(path, entry, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 写失败向上抛：工具调用方要把失败告知模型（而非静默丢失记忆）
            throw new IllegalStateException("记忆写入失败: " + e.getMessage(), e);
        }
    }
}

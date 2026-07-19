package com.agentx.chat.service.memory;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 模型轨记忆管线（取代 MessageChatMemoryAdvisor 的注入/回写两个动作）。
 * <p>
 * 收回黑盒透传的动机：进模型的版本与存记忆的版本必须不同——
 * 附件当轮注入全文、记忆存占位；助手消息入忆时追加工具轨迹摘要；
 * 读取按 token 预算裁剪；接近阈值滚动压缩。这些都需要读写两侧的加工点。
 * <p>
 * 底层仍是 Spring AI 的 {@link ChatMemory}（MessageWindowChatMemory + JDBC 持久化），
 * 语义对齐原 advisor：读=会话历史注入 prompt，写=轮次结束记 user/assistant 对；
 * 刻意的差异均在方法注释中声明。写侧按会话加锁，与压缩的 clear+add 互斥。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelMemoryService {

    /** 注入 prompt 的历史 token 预算：超出从最老开始丢（摘要对保留），并记 WARN。 */
    static final int PROMPT_BUDGET_TOKENS = 32_000;
    /** 滚动压缩触发阈值：记忆总量估算超此值即异步压缩。 */
    static final int COMPACT_TRIGGER_TOKENS = 20_000;
    /** 压缩时保留原文的最近消息数（user/assistant 对，6 轮）。 */
    static final int KEEP_RECENT_MESSAGES = 12;
    /** 摘要消息标记前缀：识别「已压缩」状态（裁剪保留 / 再压缩时滚入新摘要）。 */
    static final String SUMMARY_MARKER = "【早期对话摘要·系统压缩】\n";
    /** 摘要后的助手确认（保持 user/assistant 交替，兼容严格交替校验的供应商）。 */
    private static final String SUMMARY_ACK = "已了解上述背景，从中断处继续。";
    /** 压缩输入里单条消息的截断上限：防单条巨型消息撑爆摘要调用本身。 */
    private static final int TRANSCRIPT_CLIP_CHARS = 4_000;

    private final ChatMemory chatMemory;
    private final CompactionSummarizer summarizer;

    /** 会话级写锁：recordRound 与 compact 的 clear+add 段互斥，防交错丢消息。 */
    private final Map<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();
    /** 压缩在途标记：同会话不并发压缩（重复压缩浪费且互相覆盖）。 */
    private final Map<UUID, Boolean> compacting = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /* ---------------- 读侧：历史注入 ---------------- */

    /**
     * 取会话历史用于 prompt 注入。超 token 预算时从最老开始丢弃；
     * 头部的「摘要对」（压缩产物）始终保留——那是早期上下文的唯一存根。
     */
    public List<Message> loadHistory(UUID conversationId) {
        List<Message> messages = chatMemory.get(conversationId.toString());
        if (messages.isEmpty()) {
            return messages;
        }
        int total = TokenEstimator.estimate(messages);
        if (total <= PROMPT_BUDGET_TOKENS) {
            return messages;
        }
        // 保留头部摘要对（若有），从其后最老的消息开始丢
        int protectedHead = startsWithSummary(messages) ? 2 : 0;
        List<Message> trimmed = new ArrayList<>(messages);
        int dropped = 0;
        while (trimmed.size() > protectedHead + 2
                && TokenEstimator.estimate(trimmed) > PROMPT_BUDGET_TOKENS) {
            trimmed.remove(protectedHead);
            dropped++;
        }
        log.warn("会话 {} 历史超 token 预算（约 {} tokens），已丢弃最老 {} 条消息注入",
                conversationId, total, dropped);
        return trimmed;
    }

    /* ---------------- 写侧：轮次回写 ---------------- */

    /**
     * 轮次结束回写记忆。与原 advisor 的刻意差异：
     * ① userText 是记忆版（附件占位而非全文）；② assistantText 尾部追加工具轨迹摘要；
     * ③ 空轮次（无回复且无工具操作）不写——原 advisor 会写入空 assistant 消息。
     */
    public void recordRound(UUID conversationId, String userText, String assistantText,
                            String toolSummary) {
        String assistantMemory = joinAssistant(assistantText, toolSummary);
        if (assistantMemory.isBlank()) {
            return;
        }
        withLock(conversationId, () -> chatMemory.add(conversationId.toString(),
                List.of(new UserMessage(userText), new AssistantMessage(assistantMemory))));
    }

    /* ---------------- 压缩 ---------------- */

    /** 轮后检查：超阈值则异步压缩（不阻塞 SSE 收尾）。planState 作恢复锚点。 */
    public void maybeCompactAsync(UUID conversationId, String planState) {
        int total = TokenEstimator.estimate(chatMemory.get(conversationId.toString()));
        if (total < COMPACT_TRIGGER_TOKENS) {
            return;
        }
        if (compacting.putIfAbsent(conversationId, Boolean.TRUE) != null) {
            return; // 已有压缩在途
        }
        executor.submit(() -> {
            try {
                CompactionResult result = doCompact(conversationId, planState);
                if (result != null) {
                    log.info("会话 {} 自动压缩完成：{} 条消息 → 摘要 {} 字，估算 {} → {} tokens",
                            conversationId, result.compactedMessages(), result.summaryChars(),
                            result.tokensBefore(), result.tokensAfter());
                }
            } catch (Exception e) {
                log.warn("会话 {} 自动压缩失败（记忆保持原样）: {}", conversationId, e.getMessage());
            } finally {
                compacting.remove(conversationId);
            }
        });
    }

    /** 手动压缩（/compact）：同步执行，无可压缩内容时以业务异常提示。 */
    public CompactionResult compact(UUID conversationId, String planState) {
        if (compacting.putIfAbsent(conversationId, Boolean.TRUE) != null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "压缩正在进行中，请稍候");
        }
        try {
            CompactionResult result = doCompact(conversationId, planState);
            if (result == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "当前会话历史很短，无需压缩");
            }
            return result;
        } finally {
            compacting.remove(conversationId);
        }
    }

    public record CompactionResult(int compactedMessages, int summaryChars,
                                   int tokensBefore, int tokensAfter) {}

    /**
     * 压缩主体：head（含旧摘要，滚动并入）→ 模型摘要 → [摘要对] + tail 原文重建。
     * 摘要调用失败直接抛出——记忆保持原样，绝不半途 clear。
     */
    private CompactionResult doCompact(UUID conversationId, String planState) {
        String id = conversationId.toString();
        List<Message> messages = chatMemory.get(id);
        if (messages.size() < KEEP_RECENT_MESSAGES + 4) {
            return null; // 历史太短：压缩省不了多少反而丢细节
        }
        int tokensBefore = TokenEstimator.estimate(messages);
        int cut = messages.size() - KEEP_RECENT_MESSAGES;
        List<Message> head = messages.subList(0, cut);
        List<Message> tail = List.copyOf(messages.subList(cut, messages.size()));

        // 摘要调用在锁外（耗时秒级）：期间新到的轮次消息以尾部差量方式保留（见下）
        String summary = summarizer.summarize(formatTranscript(head));
        String anchored = SUMMARY_MARKER + summary
                + (planState == null || planState.isBlank()
                        ? "" : "\n\n当前任务清单（最新状态）：\n" + planState);

        withLock(conversationId, () -> {
            // 摘要期间可能有新轮次落库：以当前最新列表为准，cut 之前的替换为摘要对
            List<Message> latest = chatMemory.get(id);
            List<Message> rebuilt = new ArrayList<>();
            rebuilt.add(new UserMessage(anchored));
            rebuilt.add(new AssistantMessage(SUMMARY_ACK));
            rebuilt.addAll(latest.subList(Math.min(cut, latest.size()), latest.size()));
            chatMemory.clear(id);
            chatMemory.add(id, rebuilt);
        });
        int tokensAfter = TokenEstimator.estimate(chatMemory.get(id));
        return new CompactionResult(cut, summary.length(), tokensBefore, tokensAfter);
    }

    /* ---------------- 辅助 ---------------- */

    /** 当前记忆的 token 估算（供前端余量指示/诊断）。 */
    public int estimateTokens(UUID conversationId) {
        return TokenEstimator.estimate(chatMemory.get(conversationId.toString()));
    }

    /** 上下文用量视图：前端余量指示环的数据源（tokens 为启发式约数）。 */
    public record ContextUsage(int tokens, int compactThreshold, int promptBudget) {}

    public ContextUsage usage(UUID conversationId) {
        return new ContextUsage(estimateTokens(conversationId),
                COMPACT_TRIGGER_TOKENS, PROMPT_BUDGET_TOKENS);
    }

    private static boolean startsWithSummary(List<Message> messages) {
        String first = messages.get(0).getText();
        return first != null && first.startsWith(SUMMARY_MARKER);
    }

    private static String joinAssistant(String assistantText, String toolSummary) {
        String text = assistantText == null ? "" : assistantText.strip();
        String summary = toolSummary == null ? "" : toolSummary.strip();
        if (summary.isEmpty()) {
            return text;
        }
        return text.isEmpty() ? summary : text + "\n\n" + summary;
    }

    private static String formatTranscript(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            String text = m.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            String role = switch (m.getMessageType()) {
                case USER -> "用户";
                case ASSISTANT -> "助手";
                default -> "系统";
            };
            sb.append(role).append(": ").append(clip(text)).append("\n\n");
        }
        return sb.toString();
    }

    private static String clip(String text) {
        return text.length() <= TRANSCRIPT_CLIP_CHARS
                ? text : text.substring(0, TRANSCRIPT_CLIP_CHARS) + "\n…（本条已截断）";
    }

    private void withLock(UUID conversationId, Runnable action) {
        ReentrantLock lock = locks.computeIfAbsent(conversationId, k -> new ReentrantLock());
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}

package com.agentx.chat.service;

import com.agentx.infra.ai.client.ChatClientFactory;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 会话标题自动生成（设计文档 §4.4「首轮后异步用小模型生成」）。
 * <p>
 * 首轮对话（用户消息 + 助手应答共 2 条）结束后，用默认 CHAT 模型据上下文
 * 生成一个简短标题覆盖截断式默认标题。异步执行——不阻塞 SSE done 帧；
 * 任何失败仅记日志并保留默认标题，绝不影响主链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationTitleGenerator {

    private static final String PROMPT = """
            为下面这轮对话起一个简短标题，要求：不超过 12 个字、概括主题、\
            纯文本、不要引号书名号或标点、不要“标题：”之类前缀。只输出标题本身。

            用户：%s
            助手：%s
            """;
    private static final int MAX_TITLE_LEN = 24;

    private final ChatClientFactory chatClientFactory;
    private final ConversationService conversationService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /** 仅首轮触发；异步生成，失败静默保留默认标题。 */
    public void maybeGenerateAsync(UUID conversationId, String userContent, String assistantContent) {
        if (assistantContent == null || assistantContent.isBlank()) {
            return; // 无有效应答（如纯工具轮/错误）不生成
        }
        executor.submit(() -> {
            try {
                if (conversationService.messageCount(conversationId) != 2) {
                    return; // 非首轮（并发/重生成）——不覆盖已有标题
                }
                String raw = chatClientFactory.getDefault().prompt()
                        .user(PROMPT.formatted(clip(userContent, 500), clip(assistantContent, 500)))
                        .call()
                        .content();
                String title = sanitize(raw);
                if (!title.isBlank()) {
                    conversationService.applyGeneratedTitle(conversationId, title);
                }
            } catch (Exception e) {
                log.warn("会话标题生成失败 conversation={}（保留默认标题）: {}", conversationId, e.getMessage());
            }
        });
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** 去掉模型可能带的引号/换行/多余前缀，并截断到上限。 */
    private static String sanitize(String raw) {
        if (raw == null) return "";
        String t = raw.strip()
                .replaceAll("^[\"'“”『「]+|[\"'“”』」]+$", "")
                .replaceAll("^(标题|title)\\s*[:：]\\s*", "")
                .replace("\n", " ")
                .strip();
        return t.length() > MAX_TITLE_LEN ? t.substring(0, MAX_TITLE_LEN) : t;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}

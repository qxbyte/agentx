package com.agentx.infra.ai.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户提问未决表（askUserQuestion 工具，机制同 {@link ApprovalRegistry}）：
 * 工具线程阻塞在登记的 future 上，等前端提交答案后 complete（值为答案 JSON）。
 * 断流/会话结束以 null 收尾（视同未作答），避免线程悬挂。
 */
@Slf4j
@Component
public class QuestionRegistry {

    private final Map<UUID, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
    private final Map<UUID, java.util.Set<UUID>> byConversation = new ConcurrentHashMap<>();
    /** 提问归属：questionId → 发起会话的用户 id，回传端点据此校验归属。 */
    private final Map<UUID, UUID> owner = new ConcurrentHashMap<>();

    /** 登记一个待回答项，返回工具线程要阻塞等待的 future（值为答案 JSON，null=未作答）。 */
    public CompletableFuture<String> register(UUID userId, UUID conversationId, UUID questionId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(questionId, future);
        owner.put(questionId, userId);
        byConversation.computeIfAbsent(conversationId, k -> ConcurrentHashMap.newKeySet()).add(questionId);
        return future;
    }

    /** 前端提交答案：仅归属用户可解决；归属不符视同未命中（不泄漏存在性）。 */
    public boolean resolve(UUID questionId, UUID requesterId, String answersJson) {
        UUID ownerId = owner.get(questionId);
        if (ownerId == null) {
            return false;
        }
        if (!ownerId.equals(requesterId)) {
            log.warn("拒绝越权作答：user={} 尝试回答不属于其会话的提问 question={}", requesterId, questionId);
            return false;
        }
        owner.remove(questionId);
        CompletableFuture<String> future = pending.remove(questionId);
        if (future == null) {
            return false;
        }
        future.complete(answersJson);
        return true;
    }

    /** 会话结束/断流：未决提问一律以 null（未作答）收尾，解冻悬挂的工具线程。 */
    public void cancelConversation(UUID conversationId) {
        java.util.Set<UUID> ids = byConversation.remove(conversationId);
        if (ids == null) {
            return;
        }
        for (UUID id : ids) {
            owner.remove(id);
            CompletableFuture<String> future = pending.remove(id);
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
        }
    }

    /** 登记项完成后清理会话索引与归属。 */
    public void forget(UUID conversationId, UUID questionId) {
        owner.remove(questionId);
        java.util.Set<UUID> ids = byConversation.get(conversationId);
        if (ids != null) {
            ids.remove(questionId);
        }
    }
}

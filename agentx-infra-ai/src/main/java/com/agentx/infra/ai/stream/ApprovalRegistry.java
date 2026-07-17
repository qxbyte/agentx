package com.agentx.infra.ai.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 审批未决表（设计文档 §6）：CodeAgent Ask 模式下，危险工具线程阻塞在
 * 这里登记的 future 上，等前端经回传端点 complete。
 * <p>
 * 按会话分组便于断流/结束时统一清理（未决 future 一律以拒绝收尾，避免线程悬挂）。
 * bean 单例、全局共享——装饰器（coding）注册，回传端点（coding）解决。
 */
@Slf4j
@Component
public class ApprovalRegistry {

    private final Map<UUID, CompletableFuture<Boolean>> pending = new ConcurrentHashMap<>();
    private final Map<UUID, java.util.Set<UUID>> byConversation = new ConcurrentHashMap<>();
    /** 审批项归属：approvalId → 发起会话的用户 id，回传端点据此校验归属。 */
    private final Map<UUID, UUID> owner = new ConcurrentHashMap<>();

    /** 登记一个待审批项，返回工具线程要阻塞等待的 future。 */
    public CompletableFuture<Boolean> register(UUID userId, UUID conversationId, UUID approvalId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pending.put(approvalId, future);
        owner.put(approvalId, userId);
        byConversation.computeIfAbsent(conversationId, k -> ConcurrentHashMap.newKeySet()).add(approvalId);
        return future;
    }

    /**
     * 前端回传决定：仅当 requesterId 是该审批的归属用户时才解决对应 future。
     * 归属不符视同未命中（不向调用方泄漏审批项是否存在），仅记警告日志。
     */
    public boolean resolve(UUID approvalId, UUID requesterId, boolean approved) {
        UUID ownerId = owner.get(approvalId);
        if (ownerId == null) {
            return false;
        }
        if (!ownerId.equals(requesterId)) {
            log.warn("拒绝越权审批：user={} 尝试处理不属于其会话的审批 approval={}", requesterId, approvalId);
            return false;
        }
        owner.remove(approvalId);
        CompletableFuture<Boolean> future = pending.remove(approvalId);
        if (future == null) {
            return false;
        }
        future.complete(approved);
        return true;
    }

    /**
     * 模式切到 AUTO 的即时生效：把该用户在此会话的全部未决审批一次性批准，
     * 解冻阻塞中的工具线程直接执行。归属不符的登记项跳过（防越权批量批准）。
     */
    public int approveConversation(UUID conversationId, UUID requesterId) {
        java.util.Set<UUID> ids = byConversation.get(conversationId);
        if (ids == null) {
            return 0;
        }
        int approved = 0;
        for (UUID id : java.util.List.copyOf(ids)) {
            if (requesterId.equals(owner.get(id)) && resolve(id, requesterId, true)) {
                approved++;
            }
        }
        return approved;
    }

    /** 会话结束/断流：未决审批一律以拒绝收尾，解冻悬挂的工具线程。 */
    public void cancelConversation(UUID conversationId) {
        java.util.Set<UUID> ids = byConversation.remove(conversationId);
        if (ids == null) {
            return;
        }
        for (UUID id : ids) {
            owner.remove(id);
            CompletableFuture<Boolean> future = pending.remove(id);
            if (future != null && !future.isDone()) {
                future.complete(false);
            }
        }
    }

    /** 登记项完成后清理会话索引与归属。 */
    public void forget(UUID conversationId, UUID approvalId) {
        owner.remove(approvalId);
        java.util.Set<UUID> ids = byConversation.get(conversationId);
        if (ids != null) {
            ids.remove(approvalId);
        }
    }
}

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

    /** 登记一个待审批项，返回工具线程要阻塞等待的 future。 */
    public CompletableFuture<Boolean> register(UUID conversationId, UUID approvalId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pending.put(approvalId, future);
        byConversation.computeIfAbsent(conversationId, k -> ConcurrentHashMap.newKeySet()).add(approvalId);
        return future;
    }

    /** 前端回传决定：解决对应 future；返回是否命中一个未决项。 */
    public boolean resolve(UUID approvalId, boolean approved) {
        CompletableFuture<Boolean> future = pending.remove(approvalId);
        if (future == null) {
            return false;
        }
        future.complete(approved);
        return true;
    }

    /** 会话结束/断流：未决审批一律以拒绝收尾，解冻悬挂的工具线程。 */
    public void cancelConversation(UUID conversationId) {
        java.util.Set<UUID> ids = byConversation.remove(conversationId);
        if (ids == null) {
            return;
        }
        for (UUID id : ids) {
            CompletableFuture<Boolean> future = pending.remove(id);
            if (future != null && !future.isDone()) {
                future.complete(false);
            }
        }
    }

    /** 登记项完成后清理会话索引。 */
    public void forget(UUID conversationId, UUID approvalId) {
        java.util.Set<UUID> ids = byConversation.get(conversationId);
        if (ids != null) {
            ids.remove(approvalId);
        }
    }
}

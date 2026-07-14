package com.agentx.chat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 每用户并发流上限（设计文档 §9 资源保护）：限制单个用户同时进行的 SSE 流式对话数，
 * 防止一个账号开大量长连接拖垮模型调用与连接资源。基于按用户的信号量，
 * {@link #tryAcquire} 成功返回一次性释放句柄，失败返回 null（调用方据此拒绝）。
 */
@Component
public class ConcurrentStreamLimiter {

    private final int maxPerUser;
    private final Map<UUID, Semaphore> perUser = new ConcurrentHashMap<>();

    public ConcurrentStreamLimiter(
            @Value("${agentx.chat.max-concurrent-streams-per-user:3}") int maxPerUser) {
        this.maxPerUser = Math.max(1, maxPerUser);
    }

    /**
     * 尝试占用一个并发额度。成功返回幂等的释放句柄（多次调用只释放一次），
     * 达到上限返回 null。
     */
    public Runnable tryAcquire(UUID userId) {
        Semaphore sem = perUser.computeIfAbsent(userId, k -> new Semaphore(maxPerUser));
        if (!sem.tryAcquire()) {
            return null;
        }
        AtomicBoolean released = new AtomicBoolean(false);
        return () -> {
            if (released.compareAndSet(false, true)) {
                sem.release();
            }
        };
    }
}

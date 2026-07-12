package com.agentx.infra.ai.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SseEmitter 的线程安全发送封装。
 * <p>
 * 解决三个原生痛点：并发 send 竞态、客户端断连后 send 抛 IO 异常污染业务流、
 * complete/completeWithError 重复调用。终结后所有 send 静默丢弃（幂等）。
 */
@Slf4j
public class SseEmitterSender {
    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean terminated = new AtomicBoolean(false);

    public SseEmitterSender(SseEmitter emitter, ObjectMapper objectMapper) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
        emitter.onCompletion(() -> terminated.set(true));
        emitter.onTimeout(() -> terminated.set(true));
        emitter.onError(e -> terminated.set(true));
    }

    /** 发送一个信封事件；连接已终结时静默丢弃并返回 false。 */
    public synchronized boolean send(SseEvent event) {
        if (terminated.get()) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(event.toPayload())));
            return true;
        } catch (Exception e) {
            log.debug("SSE send failed (client likely disconnected): {}", e.getMessage());
            terminated.set(true);
            return false;
        }
    }

    /** 发送注释心跳帧，维持代理链路不超时。 */
    public synchronized void heartbeat() {
        if (terminated.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().comment("ping"));
        } catch (Exception e) {
            terminated.set(true);
        }
    }

    public void complete() {
        if (terminated.compareAndSet(false, true)) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 已断连
            }
        }
    }

    public boolean isTerminated() {
        return terminated.get();
    }
}

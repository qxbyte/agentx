package com.agentx.infra.ai.stream;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 一次流式对话的请求上下文，供 {@link ChatStreamCustomizer} 消费。
 * <p>
 * kbIds 为可变集合：chat 层放入会话绑定的知识库，Agent 定制器（@Order 靠前）
 * 可合并 Agent 绑定的知识库，RAG 定制器（@Order 靠后）统一消费。
 */
public record ChatStreamContext(
        UUID userId,
        UUID conversationId,
        UUID agentId,
        Set<UUID> kbIds,
        ToolEventSink toolEventSink
) {
    public static ChatStreamContext of(UUID userId, UUID conversationId, UUID agentId,
                                       Set<UUID> initialKbIds, ToolEventSink sink) {
        return new ChatStreamContext(userId, conversationId, agentId,
                new LinkedHashSet<>(initialKbIds), sink);
    }
}

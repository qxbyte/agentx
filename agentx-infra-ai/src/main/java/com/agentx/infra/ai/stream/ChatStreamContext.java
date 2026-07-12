package com.agentx.infra.ai.stream;

import java.util.UUID;

/** 一次流式对话的请求上下文，供 {@link ChatStreamCustomizer} 消费。 */
public record ChatStreamContext(
        UUID userId,
        UUID conversationId,
        UUID agentId,
        ToolEventSink toolEventSink
) {}

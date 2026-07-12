package com.agentx.chat.web.dto;

import com.agentx.chat.domain.ChatConversation;
import com.agentx.chat.domain.ChatMessage;
import com.agentx.chat.domain.MessageRole;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public final class ChatDtos {
    private ChatDtos() {}

    public record CreateConversationRequest(UUID modelConfigId, UUID agentId,
                                            java.util.List<UUID> kbIds) {}

    public record RenameRequest(@NotBlank String title) {}

    public record ConversationView(UUID id, String title, UUID agentId, UUID modelConfigId,
                                   Instant createdAt, Instant updatedAt) {
        public static ConversationView of(ChatConversation c) {
            return new ConversationView(c.getId(), c.getTitle(), c.getAgentId(),
                    c.getModelConfigId(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    public record MessageView(UUID id, MessageRole role, String content, String reasoningContent,
                              String toolCalls, String ragSources, String tokenUsage, Instant createdAt) {
        public static MessageView of(ChatMessage m) {
            return new MessageView(m.getId(), m.getRole(), m.getContent(), m.getReasoningContent(),
                    m.getToolCalls(), m.getRagSources(), m.getTokenUsage(), m.getCreatedAt());
        }
    }

    /** 流式对话请求：conversationId 为空则自动建会话。 */
    public record StreamRequest(UUID conversationId, @NotBlank String content, UUID modelConfigId) {}
}

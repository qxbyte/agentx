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
                                   UUID workspaceId, Instant createdAt, Instant updatedAt) {
        public static ConversationView of(ChatConversation c) {
            return new ConversationView(c.getId(), c.getTitle(), c.getAgentId(),
                    c.getModelConfigId(), c.getWorkspaceId(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    public record MessageView(UUID id, MessageRole role, String content, String reasoningContent,
                              String toolCalls, String ragSources, String tokenUsage, Instant createdAt) {
        public static MessageView of(ChatMessage m) {
            return new MessageView(m.getId(), m.getRole(), m.getContent(), m.getReasoningContent(),
                    m.getToolCalls(), m.getRagSources(), m.getTokenUsage(), m.getCreatedAt());
        }
    }

    /** 流式对话请求：conversationId 为空则自动建会话；workspaceId 非空激活 CodeAgent；
     *  kbIds 为本次检索追加的知识库（输入框多选，与会话/工作区默认知识库合并）。 */
    public record StreamRequest(UUID conversationId, @NotBlank String content, UUID modelConfigId,
                               UUID workspaceId, String mode, java.util.List<UUID> kbIds) {}

    /** 重新生成请求（可选覆盖模型/模式，均为空则沿用会话既定配置）。 */
    public record RegenerateRequest(UUID modelConfigId, String mode) {}
}

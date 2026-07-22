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
                                   UUID workspaceId, String planState,
                                   Instant createdAt, Instant updatedAt) {
        public static ConversationView of(ChatConversation c) {
            return new ConversationView(c.getId(), c.getTitle(), c.getAgentId(),
                    c.getModelConfigId(), c.getWorkspaceId(), c.getPlanState(),
                    c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    public record MessageView(UUID id, MessageRole role, String content, String blocks,
                              String ragSources, String tokenUsage,
                              String attachments, Instant createdAt) {
        public static MessageView of(ChatMessage m) {
            return new MessageView(m.getId(), m.getRole(), m.getContent(), m.getBlocks(),
                    m.getRagSources(), m.getTokenUsage(),
                    m.getAttachments(), m.getCreatedAt());
        }
    }

    /** 流式对话请求：conversationId 为空则自动建会话；workspaceId 非空激活 CodeAgent；
     *  kbIds 为本次检索追加的知识库（输入框多选，与会话/工作区默认知识库合并）；
     *  attachmentIds 为本条消息携带的已上传附件；
     *  useDefaultModel=true 表示用户显式切回「默认模型」——清除会话固化的模型选择
     *  （modelConfigId 为空无法区分「未选择」与「选默认」，需显式标记）。 */
    public record StreamRequest(UUID conversationId, @NotBlank String content, UUID modelConfigId,
                               UUID workspaceId, String mode, java.util.List<UUID> kbIds,
                               java.util.List<UUID> attachmentIds, Boolean useDefaultModel) {}

    /** 重新生成请求（可选覆盖模型/模式，均为空则沿用会话既定配置）。 */
    public record RegenerateRequest(UUID modelConfigId, String mode) {}
}

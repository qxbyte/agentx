package com.agentx.chat.service;

import com.agentx.chat.domain.ChatConversation;
import com.agentx.chat.domain.ChatConversationRepository;
import com.agentx.chat.domain.ChatMessage;
import com.agentx.chat.domain.ChatMessageRepository;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.common.util.UuidV7;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 会话生命周期管理（业务轨）。所有查询按 userId 隔离——
 * 越权访问表现为 404 而非 403，不泄露资源存在性。
 */
@Service
@RequiredArgsConstructor
public class ConversationService {
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatMemory chatMemory;

    public List<ChatConversation> list(UUID userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public ChatConversation getOwned(UUID id, UUID userId) {
        return conversationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "会话不存在"));
    }

    @Transactional
    public ChatConversation create(UUID userId, UUID modelConfigId, UUID agentId,
                                   java.util.List<UUID> kbIds, UUID workspaceId) {
        ChatConversation c = new ChatConversation();
        c.setId(UuidV7.next());
        c.setUserId(userId);
        c.setModelConfigId(modelConfigId);
        c.setAgentId(agentId);
        c.setWorkspaceId(workspaceId);
        if (kbIds != null && !kbIds.isEmpty()) {
            c.setKbIds("[" + kbIds.stream().map(id -> "\"" + id + "\"")
                    .collect(java.util.stream.Collectors.joining(",")) + "]");
        }
        return conversationRepository.save(c);
    }

    @Transactional
    public ChatConversation rename(UUID id, UUID userId, String title) {
        ChatConversation c = getOwned(id, userId);
        c.setTitle(title);
        c.setUpdatedAt(Instant.now());
        return conversationRepository.save(c);
    }

    /** 双轨同事务删除：业务消息（级联）+ 模型轨记忆。 */
    @Transactional
    public void delete(UUID id, UUID userId) {
        ChatConversation c = getOwned(id, userId);
        conversationRepository.delete(c);
        chatMemory.clear(id.toString());
    }

    public List<ChatMessage> messages(UUID conversationId, UUID userId) {
        getOwned(conversationId, userId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    /** 首条用户消息后的默认标题：截取内容前 20 字。 */
    @Transactional
    public void applyDefaultTitle(ChatConversation c, String firstUserContent) {
        if (!"新对话".equals(c.getTitle())) {
            return;
        }
        String title = firstUserContent.strip();
        c.setTitle(title.length() > 20 ? title.substring(0, 20) : title);
        c.setUpdatedAt(Instant.now());
        conversationRepository.save(c);
    }

    @Transactional
    public ChatMessage saveMessage(ChatMessage message) {
        if (message.getId() == null) {
            message.setId(UuidV7.next());
        }
        return messageRepository.save(message);
    }

    @Transactional
    public void touch(ChatConversation c) {
        c.setUpdatedAt(Instant.now());
        conversationRepository.save(c);
    }
}

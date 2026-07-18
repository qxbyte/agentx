package com.agentx.chat.service;

import com.agentx.chat.domain.ChatConversation;
import com.agentx.chat.domain.ChatConversationRepository;
import com.agentx.chat.domain.ChatMessage;
import com.agentx.chat.domain.ChatMessageRepository;
import com.agentx.chat.domain.MessageRole;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.common.util.UuidV7;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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

    /** 首条用户消息后的默认标题：截取内容前 20 字（后续由小模型异步改写为更贴切的标题）。 */
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

    public long messageCount(UUID conversationId) {
        return messageRepository.countByConversationId(conversationId);
    }

    /** 小模型生成的标题回写（异步任务调用，已在鉴权流程内，无需再校验归属）。 */
    @Transactional
    public void applyGeneratedTitle(UUID conversationId, String title) {
        conversationRepository.findById(conversationId).ifPresent(c -> {
            c.setTitle(title);
            c.setUpdatedAt(Instant.now());
            conversationRepository.save(c);
        });
    }

    /** 会话记住模型选择：本轮显式选择的模型回写会话，刷新/重开后沿用（除非再次切换）。 */
    @Transactional
    public void rememberModelChoice(ChatConversation c, UUID modelConfigId) {
        if (modelConfigId == null || modelConfigId.equals(c.getModelConfigId())) {
            return;
        }
        c.setModelConfigId(modelConfigId);
        conversationRepository.save(c);
    }

    /** 用户显式切回「默认模型」：清除会话固化的模型选择。 */
    public void clearModelChoice(ChatConversation c) {
        if (c.getModelConfigId() == null) {
            return;
        }
        c.setModelConfigId(null);
        conversationRepository.save(c);
    }

    /** updatePlan 工具回写：持久化会话最新计划（参数原文 JSON，前端刷新/切换会话恢复面板）。 */
    @Transactional
    public void updatePlanState(UUID conversationId, String planJson) {
        conversationRepository.findById(conversationId).ifPresent(c -> {
            c.setPlanState(planJson);
            conversationRepository.save(c);
        });
    }

    @Transactional
    public ChatMessage saveMessage(ChatMessage message) {
        if (message.getId() == null) {
            message.setId(UuidV7.next());
        }
        return messageRepository.save(message);
    }

    /** 按 id 事务内新读再更新时间戳：不能直接保存流开始时加载的过期实体——
     *  JPA 全字段合并会把流中途写入的字段（如 updatePlan 回写的 plan_state）抹回旧值。 */
    @Transactional
    public void touch(UUID conversationId) {
        conversationRepository.findById(conversationId).ifPresent(c -> {
            c.setUpdatedAt(Instant.now());
            conversationRepository.save(c);
        });
    }

    /** 重新生成的准备结果：目标会话 + 待复用的用户提问 + 会话工作区（供 coding 续接）。 */
    public record RegenerateContext(UUID conversationId, String userContent, UUID workspaceId) {}

    /**
     * 为「重新生成」清场（设计文档 §4.4 消息重新生成）：定位待重生成的助手消息及其
     * 对应的用户提问，删除该轮起的全部业务消息，并把模型轨记忆回滚到该轮之前，
     * 返回用户提问供上层复用现有流式链路重跑。两轨一致：业务表与 ChatMemory 同步回退。
     */
    @Transactional
    public RegenerateContext prepareRegenerate(UUID assistantMessageId, UUID userId) {
        ChatMessage assistant = messageRepository.findById(assistantMessageId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "待重新生成的消息不存在"));
        if (assistant.getRole() != MessageRole.ASSISTANT) {
            throw new BizException(ErrorCode.BAD_REQUEST, "只能对助手消息执行重新生成");
        }
        ChatConversation conversation = getOwned(assistant.getConversationId(), userId);

        List<ChatMessage> all = messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        int assistantIdx = indexOfId(all, assistantMessageId);
        int userIdx = -1;
        for (int i = assistantIdx - 1; i >= 0; i--) {
            if (all.get(i).getRole() == MessageRole.USER) {
                userIdx = i;
                break;
            }
        }
        if (userIdx < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "找不到对应的用户提问，无法重新生成");
        }
        String userContent = all.get(userIdx).getContent();

        // 业务轨：删除该轮用户提问起的全部消息（含目标助手消息及其后续）
        messageRepository.deleteAll(all.subList(userIdx, all.size()));
        // 模型轨：清空后按保留的历史重建，回滚到该轮之前的上下文
        chatMemory.clear(conversation.getId().toString());
        List<Message> history = all.subList(0, userIdx).stream()
                .map(ConversationService::toMemoryMessage)
                .filter(Objects::nonNull)
                .toList();
        if (!history.isEmpty()) {
            chatMemory.add(conversation.getId().toString(), history);
        }
        return new RegenerateContext(conversation.getId(), userContent, conversation.getWorkspaceId());
    }

    private static int indexOfId(List<ChatMessage> messages, UUID id) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(id)) {
                return i;
            }
        }
        throw new BizException(ErrorCode.NOT_FOUND, "消息不在会话中");
    }

    /** 业务消息 → 模型轨记忆消息（仅取正文，工具/系统消息不入记忆重建）。 */
    private static Message toMemoryMessage(ChatMessage m) {
        return switch (m.getRole()) {
            case USER -> new UserMessage(m.getContent());
            case ASSISTANT -> m.getContent() == null || m.getContent().isBlank()
                    ? null : new AssistantMessage(m.getContent());
            default -> null;
        };
    }
}

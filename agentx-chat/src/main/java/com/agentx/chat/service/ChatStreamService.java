package com.agentx.chat.service;

import com.agentx.auth.security.AuthPrincipal;
import com.agentx.chat.domain.ChatConversation;
import com.agentx.chat.domain.ChatMessage;
import com.agentx.chat.domain.MessageRole;
import com.agentx.chat.web.dto.ChatDtos.StreamRequest;
import com.agentx.common.util.UuidV7;
import com.agentx.infra.ai.audit.AiCallAuditor;
import com.agentx.infra.ai.client.ChatClientFactory;
import com.agentx.infra.ai.sse.SseEmitterSender;
import com.agentx.infra.ai.sse.SseEvent;
import com.agentx.infra.ai.stream.ChatStreamContext;
import com.agentx.infra.ai.stream.ChatStreamCustomizer;
import com.agentx.infra.ai.stream.ToolEventSink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 流式对话主链路（设计文档 §8.1）：
 * 用户消息落库 → ChatClient(memory advisor) 流式调用 → SSE 信封推送 →
 * 完成后聚合落 ASSISTANT 消息 + 审计。全程业务错误走 error 帧，不断流。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatStreamService {

    private static final long SSE_TIMEOUT_MS = 300_000L;
    /** delta 合帧：最多 8 个 token 或 50ms 一帧，避免前端渲染风暴。 */
    private static final int FRAME_MAX_ITEMS = 8;
    private static final Duration FRAME_MAX_WAIT = Duration.ofMillis(50);

    private final ConversationService conversationService;
    private final ChatClientFactory chatClientFactory;
    private final ChatMemory chatMemory;
    private final AiCallAuditor auditor;
    private final ObjectMapper objectMapper;
    private final List<ChatStreamCustomizer> customizers;
    private final com.agentx.infra.ai.stream.ApprovalRegistry approvalRegistry;

    public SseEmitter stream(AuthPrincipal user, StreamRequest req) {
        ChatConversation conversation = resolveConversation(user, req);

        ChatMessage userMessage = new ChatMessage();
        userMessage.setConversationId(conversation.getId());
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent(req.content());
        conversationService.saveMessage(userMessage);
        conversationService.applyDefaultTitle(conversation, req.content());

        UUID assistantMessageId = UuidV7.next();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        SseEmitterSender sender = new SseEmitterSender(emitter, objectMapper);
        sender.send(SseEvent.meta(conversation.getId().toString(), assistantMessageId.toString()));

        // Ask 模式兜底：流结束/超时/客户端断开时，解冻仍挂起的审批线程，避免虚拟线程泄漏
        // （正常"批准/拒绝"由回传端点 ApprovalRegistry.resolve 完成，这里只兜住异常终止路径）
        emitter.onCompletion(() -> approvalRegistry.cancelConversation(conversation.getId()));
        emitter.onTimeout(() -> approvalRegistry.cancelConversation(conversation.getId()));
        emitter.onError(e -> approvalRegistry.cancelConversation(conversation.getId()));

        ChatClient client = resolveClient(conversation, req);
        StreamAggregator aggregator = new StreamAggregator(user, conversation, assistantMessageId);

        ToolEventSink toolEventSink = new SseToolEventSink(sender, aggregator);
        ChatStreamContext context = ChatStreamContext.of(
                user.id(), conversation.getId(), conversation.getAgentId(),
                parseKbIds(conversation.getKbIds()), req.workspaceId(), req.mode(), toolEventSink);

        ChatClient.ChatClientRequestSpec spec = client.prompt()
                .user(req.content())
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversation.getId().toString()))
                .toolContext(Map.of(
                        "userId", user.id().toString(),
                        "conversationId", conversation.getId().toString()));
        customizers.forEach(c -> c.customize(context, spec));

        // chatClientResponse 流：既有模型增量，也携带 advisor 上下文（RAG 命中文档）
        Flux<org.springframework.ai.chat.client.ChatClientResponse> flux =
                spec.stream().chatClientResponse();

        flux.bufferTimeout(FRAME_MAX_ITEMS, FRAME_MAX_WAIT)
                .subscribe(
                        batch -> batch.forEach(r -> aggregator.onChunk(r, sender)),
                        error -> aggregator.onError(error, sender),
                        () -> aggregator.onComplete(sender));
        return emitter;
    }

    private java.util.Set<UUID> parseKbIds(String kbIdsJson) {
        if (kbIdsJson == null || kbIdsJson.isBlank()) {
            return java.util.Set.of();
        }
        List<UUID> ids = objectMapper.readValue(kbIdsJson,
                new tools.jackson.core.type.TypeReference<List<UUID>>() {});
        return new java.util.LinkedHashSet<>(ids);
    }

    private ChatConversation resolveConversation(AuthPrincipal user, StreamRequest req) {
        if (req.conversationId() != null) {
            return conversationService.getOwned(req.conversationId(), user.id());
        }
        return conversationService.create(user.id(), req.modelConfigId(), null, null);
    }

    private ChatClient resolveClient(ChatConversation conversation, StreamRequest req) {
        UUID modelConfigId = req.modelConfigId() != null
                ? req.modelConfigId() : conversation.getModelConfigId();
        return modelConfigId != null
                ? chatClientFactory.get(modelConfigId)
                : chatClientFactory.getDefault();
    }

    /** 工具事件 → SSE 帧 + 聚合器记录（最终随 ASSISTANT 消息落库）。 */
    private record SseToolEventSink(SseEmitterSender sender, StreamAggregator aggregator)
            implements ToolEventSink {
        @Override
        public void onToolCall(String callId, String toolName, String argsJson) {
            aggregator.recordToolCall(callId, toolName, argsJson);
            sender.send(SseEvent.toolCall(callId, toolName, argsJson));
        }

        @Override
        public void onToolResult(String callId, String toolName, String result) {
            String truncated = result != null && result.length() > 2000
                    ? result.substring(0, 2000) + "…" : result;
            aggregator.recordToolResult(callId, truncated);
            sender.send(SseEvent.toolResult(callId, toolName, truncated));
        }

        @Override
        public void onApprovalRequest(String approvalId, String toolName, String kind,
                                      java.util.Map<String, Object> preview) {
            sender.send(SseEvent.approvalRequest(approvalId, toolName, kind, preview));
        }
    }

    /** 聚合一次流式应答的可变状态，终态负责落库/审计/终帧。 */
    private class StreamAggregator {
        private final AuthPrincipal user;
        private final ChatConversation conversation;
        private final UUID assistantMessageId;
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final long startedAt = System.currentTimeMillis();
        private long promptTokens;
        private long completionTokens;
        private String modelName = "";
        private final java.util.List<Map<String, Object>> toolCallRecords =
                java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        private final java.util.List<Map<String, Object>> ragSources =
                java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        private static String snippet(String text) {
            if (text == null) {
                return "";
            }
            return text.length() > 120 ? text.substring(0, 120) + "…" : text;
        }

        StreamAggregator(AuthPrincipal user, ChatConversation conversation, UUID assistantMessageId) {
            this.user = user;
            this.conversation = conversation;
            this.assistantMessageId = assistantMessageId;
        }

        void recordToolCall(String callId, String toolName, String argsJson) {
            toolCallRecords.add(new java.util.LinkedHashMap<>(Map.of(
                    "id", callId, "name", toolName, "args", argsJson == null ? "" : argsJson)));
        }

        void recordToolResult(String callId, String result) {
            synchronized (toolCallRecords) {
                toolCallRecords.stream()
                        .filter(r -> callId.equals(r.get("id")))
                        .findFirst()
                        .ifPresent(r -> r.put("result", result == null ? "" : result));
            }
        }

        /** RetrievalAugmentationAdvisor 的 advisor context 键（字面量避免依赖 spring-ai-rag）。 */
        private static final String RAG_DOCUMENT_CONTEXT = "rag_document_context";

        @SuppressWarnings("unchecked")
        void onChunk(org.springframework.ai.chat.client.ChatClientResponse clientResponse,
                     SseEmitterSender sender) {
            Object docs = clientResponse.context().get(RAG_DOCUMENT_CONTEXT);
            if (docs instanceof List<?> documents && !documents.isEmpty() && ragSources.isEmpty()) {
                for (Object o : documents) {
                    if (o instanceof org.springframework.ai.document.Document d) {
                        ragSources.add(new java.util.LinkedHashMap<>(Map.of(
                                "docId", String.valueOf(d.getMetadata().get("doc_id")),
                                "docName", String.valueOf(d.getMetadata().get("doc_name")),
                                "segmentId", String.valueOf(d.getMetadata().get("segment_id")),
                                "score", d.getScore() == null ? 0.0 : d.getScore(),
                                "snippet", snippet(d.getText()))));
                    }
                }
                if (!ragSources.isEmpty()) {
                    sender.send(SseEvent.ragSource(ragSources));
                }
            }
            ChatResponse response = clientResponse.chatResponse();
            if (response == null || response.getResult() == null) {
                return;
            }
            var output = response.getResult().getOutput();
            if (output instanceof DeepSeekAssistantMessage dsm && dsm.getReasoningContent() != null
                    && !dsm.getReasoningContent().isEmpty()) {
                reasoning.append(dsm.getReasoningContent());
                sender.send(SseEvent.reasoning(dsm.getReasoningContent()));
            }
            String delta = output.getText();
            if (delta != null && !delta.isEmpty()) {
                content.append(delta);
                sender.send(SseEvent.textDelta(delta));
            }
            if (response.getMetadata() != null) {
                Usage usage = response.getMetadata().getUsage();
                if (usage != null && usage.getTotalTokens() != null && usage.getTotalTokens() > 0) {
                    promptTokens = usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
                    completionTokens = usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
                }
                if (response.getMetadata().getModel() != null) {
                    modelName = response.getMetadata().getModel();
                }
            }
        }

        void onComplete(SseEmitterSender sender) {
            try {
                persistAssistantMessage(null);
                auditor.record(user.id(), conversation.getId(), modelName,
                        promptTokens, completionTokens,
                        System.currentTimeMillis() - startedAt, AiCallAuditor.CallStatus.OK);
                sender.send(SseEvent.done(promptTokens, completionTokens, "stop"));
            } finally {
                sender.complete();
            }
        }

        void onError(Throwable error, SseEmitterSender sender) {
            log.warn("流式调用失败 conversation={}: {}", conversation.getId(), error.getMessage());
            try {
                if (!content.isEmpty()) {
                    persistAssistantMessage(error.getMessage());
                }
                auditor.record(user.id(), conversation.getId(), modelName,
                        promptTokens, completionTokens,
                        System.currentTimeMillis() - startedAt, AiCallAuditor.CallStatus.ERROR);
                sender.send(SseEvent.error("50000", "模型调用失败：" + error.getMessage()));
            } finally {
                sender.complete();
            }
        }

        private void persistAssistantMessage(String errorNote) {
            ChatMessage assistant = new ChatMessage();
            assistant.setId(assistantMessageId);
            assistant.setConversationId(conversation.getId());
            assistant.setRole(MessageRole.ASSISTANT);
            assistant.setContent(content.toString());
            assistant.setReasoningContent(reasoning.isEmpty() ? null : reasoning.toString());
            if (!toolCallRecords.isEmpty()) {
                assistant.setToolCalls(objectMapper.writeValueAsString(toolCallRecords));
            }
            if (!ragSources.isEmpty()) {
                assistant.setRagSources(objectMapper.writeValueAsString(ragSources));
            }
            if (promptTokens > 0 || completionTokens > 0) {
                assistant.setTokenUsage("{\"promptTokens\":%d,\"completionTokens\":%d}"
                        .formatted(promptTokens, completionTokens));
            }
            conversationService.saveMessage(assistant);
            conversationService.touch(conversation);
        }
    }
}

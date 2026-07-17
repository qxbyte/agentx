package com.agentx.chat.service;

import com.agentx.auth.security.AuthPrincipal;
import com.agentx.chat.domain.ChatAttachment;
import com.agentx.chat.domain.ChatConversation;
import com.agentx.chat.domain.ChatMessage;
import com.agentx.chat.domain.MessageRole;
import com.agentx.chat.web.dto.ChatDtos.StreamRequest;
import com.agentx.common.exception.BizException;
import com.agentx.common.util.UuidV7;
import com.agentx.infra.ai.audit.AiCallAuditor;
import com.agentx.infra.ai.client.ChatClientFactory;
import com.agentx.infra.ai.sse.SseEmitterSender;
import com.agentx.infra.ai.sse.SseEvent;
import com.agentx.infra.ai.stream.ChatStreamContext;
import com.agentx.infra.ai.stream.ChatStreamCustomizer;
import com.agentx.infra.ai.stream.ToolEventSink;
import com.agentx.infra.ai.stream.UserPromptTransformer;
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
    private final AttachmentService attachmentService;
    private final ChatClientFactory chatClientFactory;
    private final ChatMemory chatMemory;
    private final AiCallAuditor auditor;
    private final ObjectMapper objectMapper;
    private final List<ChatStreamCustomizer> customizers;
    private final List<UserPromptTransformer> promptTransformers;
    private final com.agentx.infra.ai.stream.ApprovalRegistry approvalRegistry;
    private final com.agentx.infra.ai.stream.QuestionRegistry questionRegistry;
    private final ConversationTitleGenerator titleGenerator;
    private final ConcurrentStreamLimiter streamLimiter;
    private final org.springframework.ai.chat.client.advisor.SafeGuardAdvisor safeGuardAdvisor;

    /**
     * 重新生成某条助手消息（设计文档 §4.4）：清除该轮消息并回滚记忆后，复用主链路
     * 以相同用户提问重跑。会话既定的知识库/工作区沿用，模型/模式可按需覆盖。
     * 并发额度先占再清场，避免清场后被并发上限拒绝导致消息丢失。
     */
    public SseEmitter regenerate(AuthPrincipal user, UUID assistantMessageId,
                                 UUID modelConfigId, String mode) {
        Runnable release = streamLimiter.tryAcquire(user.id());
        if (release == null) {
            return overLimitEmitter();
        }
        try {
            var ctx = conversationService.prepareRegenerate(assistantMessageId, user.id());
            StreamRequest req = new StreamRequest(ctx.conversationId(), ctx.userContent(),
                    modelConfigId, ctx.workspaceId(), mode, null, null, null);
            return doStream(user, req, release);
        } catch (BizException e) {
            release.run();
            return errorEmitter(String.valueOf(e.getErrorCode().getCode()), e.getMessage());
        } catch (RuntimeException e) {
            release.run();
            throw e;
        }
    }

    public SseEmitter stream(AuthPrincipal user, StreamRequest req) {
        Runnable release = streamLimiter.tryAcquire(user.id());
        if (release == null) {
            return overLimitEmitter();
        }
        try {
            return doStream(user, req, release);
        } catch (BizException e) {
            // 流建立前的业务异常（模型密钥解密失败、会话不存在等）必须以 error 帧下发：
            // 若任由异常冒泡，Accept: text/event-stream 会让 JSON 异常响应写不出去，
            // 转入 /error 派发后被安全兜底伪装成 401，前端误报「登录已过期」
            release.run();
            log.warn("流式对话建立失败 user={}: {}", user.id(), e.getMessage());
            return errorEmitter(String.valueOf(e.getErrorCode().getCode()), e.getMessage());
        } catch (RuntimeException e) {
            release.run();
            throw e;
        }
    }

    /** 并发上限拒绝：即时回一个 error 帧的短流，不占用模型资源。 */
    private SseEmitter overLimitEmitter() {
        return errorEmitter("42900", "并发对话数已达上限，请等待当前对话结束后再试");
    }

    /** 即时回一个 error 帧的短流：流建立前失败的统一出口。 */
    private SseEmitter errorEmitter(String code, String message) {
        SseEmitter emitter = new SseEmitter(5_000L);
        SseEmitterSender sender = new SseEmitterSender(emitter, objectMapper);
        sender.send(SseEvent.error(code, message));
        sender.complete();
        return emitter;
    }

    private SseEmitter doStream(AuthPrincipal user, StreamRequest req, Runnable release) {
        ChatConversation conversation = resolveConversation(user, req);

        ChatMessage userMessage = new ChatMessage();
        userMessage.setConversationId(conversation.getId());
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent(req.content());
        conversationService.saveMessage(userMessage);

        // 前置变换链（skill 斜杠命令展开等）：业务轨 content 保持用户原文，
        // 变换结果只进模型轨与记忆——与附件注入同一套「双轨」策略
        String promptContent = req.content();
        var transformContext = new UserPromptTransformer.UserPromptContext(
                user.id(), conversation.getId());
        for (UserPromptTransformer transformer : promptTransformers) {
            promptContent = transformer.transform(transformContext, promptContent);
        }

        // 附件：绑定本条消息 → 元数据落业务轨（气泡芯片）→ 全文 XML 注入模型轨
        // （业务轨 content 保持用户原文，注入文本只进 prompt/记忆，避免历史接口膨胀）
        var attachments = attachmentService.bindToMessage(
                req.attachmentIds(), user.id(), conversation.getId(), userMessage.getId());
        if (!attachments.isEmpty()) {
            userMessage.setAttachments(attachmentService.metadataJson(attachments));
            conversationService.saveMessage(userMessage);
            promptContent = attachmentService.wrapForPrompt(attachments, promptContent);
        }
        conversationService.applyDefaultTitle(conversation, req.content());

        UUID assistantMessageId = UuidV7.next();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        SseEmitterSender sender = new SseEmitterSender(emitter, objectMapper);
        sender.send(SseEvent.meta(conversation.getId().toString(), assistantMessageId.toString()));

        // 流终止（正常/超时/断开）统一收尾：解冻挂起的审批线程（避免虚拟线程泄漏）+
        // 释放并发额度。release 幂等，三条回调任一触发都只释放一次。
        Runnable finish = () -> {
            approvalRegistry.cancelConversation(conversation.getId());
            questionRegistry.cancelConversation(conversation.getId());
            release.run();
        };
        emitter.onCompletion(finish);
        emitter.onTimeout(finish);
        emitter.onError(e -> finish.run());

        // 本轮显式选择的模型固化到会话：刷新/重开历史会话后仍沿用上次选择；
        // 显式切回默认模型则清除固化值（否则「切回默认」永远不生效）
        if (Boolean.TRUE.equals(req.useDefaultModel())) {
            conversationService.clearModelChoice(conversation);
        } else {
            conversationService.rememberModelChoice(conversation, req.modelConfigId());
        }

        // 带图轮次切换多模态客户端：默认文本客户端走 DeepSeek 协议（content 为纯
        // String，结构上无法携带图片），仅 OpenAI 兼容供应商提供 vision 通道
        List<ChatAttachment> imageAttachments = attachments.stream()
                .filter(a -> "image".equals(a.getKind())).toList();
        ChatClient client;
        if (imageAttachments.isEmpty()) {
            client = resolveClient(conversation, req);
        } else {
            UUID visionConfigId = req.modelConfigId() != null
                    ? req.modelConfigId() : conversation.getModelConfigId();
            client = chatClientFactory.getVision(visionConfigId);
            if (client == null) {
                throw new BizException(com.agentx.common.api.ErrorCode.BAD_REQUEST,
                        "当前模型的接入方式不支持图片，请选择「OpenAI 兼容」供应商下的视觉模型（如 qwen-vl 系列）");
            }
            log.info("本轮消息携带图片 {} 张，走多模态客户端 modelConfigId={}（null=平台默认）",
                    imageAttachments.size(), visionConfigId);
        }
        StreamAggregator aggregator = new StreamAggregator(user, conversation, assistantMessageId, req.content());

        ToolEventSink toolEventSink =
                new SseToolEventSink(sender, aggregator, conversation.getId(), conversationService);
        ChatStreamContext context = ChatStreamContext.of(
                user.id(), conversation.getId(), conversation.getAgentId(),
                parseKbIds(conversation.getKbIds()), req.workspaceId(), req.mode(), toolEventSink);
        // 知识库是会话创建期属性：已固化在 conversation.kbIds（见 resolveConversation），
        // 续聊忽略请求级 kbIds——后续消息一律沿用会话既定知识库

        // 图片附件经 Spring AI Media 通道随本轮 user 消息发出（文本附件已包装进 promptContent）
        final String userText = promptContent;
        ChatClient.ChatClientRequestSpec spec = client.prompt()
                .user(u -> {
                    u.text(userText);
                    for (ChatAttachment img : imageAttachments) {
                        u.media(imageMimeType(img.getFilename()),
                                new org.springframework.core.io.FileSystemResource(img.getStoragePath()));
                    }
                })
                // 敏感词/prompt 注入基线防护（命中即拦截返回失败话术，不进模型）
                .advisors(safeGuardAdvisor)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversation.getId().toString()))
                .toolContext(Map.of(
                        "userId", user.id().toString(),
                        "conversationId", conversation.getId().toString(),
                        // 交互式工具（askUserQuestion）经此取 SSE sink 与登记信息
                        "chatStreamContext", context));
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

    private static org.springframework.util.MimeType imageMimeType(String filename) {
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return org.springframework.util.MimeType.valueOf(switch (ext) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "image/jpeg";
        });
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
        // 新会话：首条消息所选知识库随会话固化落库，此后沿用不可变
        return conversationService.create(user.id(), req.modelConfigId(), null, req.kbIds(), req.workspaceId());
    }

    private ChatClient resolveClient(ChatConversation conversation, StreamRequest req) {
        if (Boolean.TRUE.equals(req.useDefaultModel())) {
            return chatClientFactory.getDefault();
        }
        UUID modelConfigId = req.modelConfigId() != null
                ? req.modelConfigId() : conversation.getModelConfigId();
        return modelConfigId != null
                ? chatClientFactory.get(modelConfigId)
                : chatClientFactory.getDefault();
    }

    /** 工具事件 → SSE 帧 + 聚合器记录（最终随 ASSISTANT 消息落库）。 */
    private record SseToolEventSink(SseEmitterSender sender, StreamAggregator aggregator,
                                    UUID conversationId, ConversationService conversationService)
            implements ToolEventSink {
        /** 计划工具名（与 PlanTools#updatePlan 一致）：调用参数即计划全量，随帧下发并回写会话。 */
        private static final String PLAN_TOOL = "updatePlan";

        @Override
        public void onToolCall(String callId, String toolName, String argsJson) {
            onToolCall(callId, toolName, argsJson, null, null);
        }

        @Override
        public void onToolCall(String callId, String toolName, String argsJson, String kind,
                               java.util.Map<String, Object> preview) {
            aggregator.recordToolCall(callId, toolName, argsJson);
            if (PLAN_TOOL.equals(toolName) && argsJson != null && !argsJson.isBlank()) {
                try {
                    conversationService.updatePlanState(conversationId, argsJson);
                } catch (RuntimeException e) {
                    log.warn("计划状态回写失败 conversation={}: {}", conversationId, e.getMessage());
                }
            }
            sender.send(SseEvent.toolCall(callId, toolName, argsJson, kind, preview));
        }

        @Override
        public void onToolResult(String callId, String toolName, String result) {
            onToolResult(callId, toolName, result, null);
        }

        @Override
        public void onToolResult(String callId, String toolName, String result, String kind) {
            String truncated = result != null && result.length() > 2000
                    ? result.substring(0, 2000) + "…" : result;
            aggregator.recordToolResult(callId, truncated);
            sender.send(SseEvent.toolResult(callId, toolName, truncated, kind));
        }

        @Override
        public void onApprovalRequest(String approvalId, String toolName, String kind,
                                      java.util.Map<String, Object> preview) {
            sender.send(SseEvent.approvalRequest(approvalId, toolName, kind, preview));
        }

        @Override
        public void onApprovalResult(String approvalId, String outcome) {
            sender.send(SseEvent.approvalResult(approvalId, outcome));
        }

        @Override
        public void onQuestionRequest(String questionId, Object questions) {
            sender.send(SseEvent.questionRequest(questionId, questions));
        }

        @Override
        public void onQuestionResult(String questionId, String outcome, String answersJson) {
            sender.send(SseEvent.questionResult(questionId, outcome, answersJson));
        }
    }

    /** 聚合一次流式应答的可变状态，终态负责落库/审计/终帧。 */
    private class StreamAggregator {
        private final AuthPrincipal user;
        private final ChatConversation conversation;
        private final UUID assistantMessageId;
        private final String userContent;
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
            return text.length() > 400 ? text.substring(0, 400) + "…" : text;
        }

        private static void putIfPresent(Map<String, Object> map, String key, Object value) {
            if (value != null) {
                map.put(key, value);
            }
        }

        StreamAggregator(AuthPrincipal user, ChatConversation conversation, UUID assistantMessageId,
                         String userContent) {
            this.user = user;
            this.conversation = conversation;
            this.assistantMessageId = assistantMessageId;
            this.userContent = userContent;
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
                        var source = new java.util.LinkedHashMap<String, Object>(Map.of(
                                "docId", String.valueOf(d.getMetadata().get("doc_id")),
                                "docName", String.valueOf(d.getMetadata().get("doc_name")),
                                "segmentId", String.valueOf(d.getMetadata().get("segment_id")),
                                "score", d.getScore() == null ? 0.0 : d.getScore(),
                                "snippet", snippet(d.getText())));
                        // 来源定位（可选）：外部知识库命中带文件路径/章节链/行号区间
                        putIfPresent(source, "path", d.getMetadata().get("doc_path"));
                        putIfPresent(source, "headings", d.getMetadata().get("headings"));
                        putIfPresent(source, "startLine", d.getMetadata().get("start_line"));
                        putIfPresent(source, "endLine", d.getMetadata().get("end_line"));
                        ragSources.add(source);
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
                // 首轮结束后异步生成贴切标题（内部判定消息数==2，非首轮不覆盖）
                titleGenerator.maybeGenerateAsync(conversation.getId(), userContent, content.toString());
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

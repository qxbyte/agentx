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
import com.agentx.chat.service.memory.MemoryFileService;
import com.agentx.chat.service.memory.ModelMemoryService;
import com.agentx.chat.service.memory.ToolTraceSummary;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
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

    /** SSE 不设超时（0 = 无限）：长 agent 轮次 + askUserQuestion/审批等人机阻塞随时可能
     *  超过任何固定时长——5 分钟超时曾把等待作答中的流掐断（AsyncRequestTimeout），
     *  提问注册随之取消，用户提交时已无处投递。客户端断开由 onError/onCompletion 兜底。 */
    private static final long SSE_TIMEOUT_MS = 0L;
    /** delta 合帧：最多 8 个 token 或 50ms 一帧，避免前端渲染风暴。 */
    private static final int FRAME_MAX_ITEMS = 8;
    private static final Duration FRAME_MAX_WAIT = Duration.ofMillis(50);

    private final ConversationService conversationService;
    private final AttachmentService attachmentService;
    private final ChatClientFactory chatClientFactory;
    private final ModelMemoryService modelMemoryService;
    private final MemoryFileService memoryFileService;
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

        // 附件：绑定本条消息 → 元数据落业务轨（气泡芯片）→ 全文 XML 注入本轮 prompt。
        // 记忆版（memoryText）用占位符替代全文——历史轮次不再重复回放附件，
        // 模型需要细节时凭占位符里的 attachmentId 调 readAttachment 重读
        var attachments = attachmentService.bindToMessage(
                req.attachmentIds(), user.id(), conversation.getId(), userMessage.getId());
        String memoryText = attachmentService.wrapForMemory(attachments, promptContent);
        if (!attachments.isEmpty()) {
            userMessage.setAttachments(attachmentService.metadataJson(attachments));
            promptContent = attachmentService.wrapForPrompt(attachments, promptContent);
        }
        // regenerate 保真：记忆版文本 ≠ 用户原文时落业务轨，模型轨重建按此回放
        if (!memoryText.equals(req.content())) {
            userMessage.setModelContent(memoryText);
        }
        if (!attachments.isEmpty() || userMessage.getModelContent() != null) {
            conversationService.saveMessage(userMessage);
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
        StreamAggregator aggregator = new StreamAggregator(
                user, conversation, assistantMessageId, req.content(), memoryText);

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
                // 记忆管线自管注入/回写（取代 MessageChatMemoryAdvisor）：
                // 读=用户级 md 记忆 + 预算裁剪后的会话历史；写=流结束 recordRound
                .messages(promptMessages(conversation))
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

    /**
     * 平台身份锚定：所有会话注入。没有它，模型会从上下文风格「推断」自己的身份——
     * 无锚定时模型可能据上下文行文风格误判自己的身份。
     */
    private static final String IDENTITY_PROMPT = """
            你是 AgentX 平台的 AI 助手，具备工具调用、知识库检索、附件解析与长期记忆能力。
            当被问及身份或底层模型时：如实说明你是 AgentX 的 AI 助手，由用户在平台配置的\
            大语言模型驱动（具体型号以界面模型选择器为准）；不要臆测或自称为任何其他 AI 产品——\
            上下文中工具说明的行文风格与你的身份无关。""";

    /** prompt 前部消息：身份锚定 + 用户级 md 记忆（合并为单条 system，稳定前缀保 KV-cache）+ 预算裁剪后的会话历史。 */
    private List<Message> promptMessages(ChatConversation conversation) {
        List<Message> result = new java.util.ArrayList<>();
        StringBuilder system = new StringBuilder(IDENTITY_PROMPT);
        String userMemory = memoryFileService.readUserMemory();
        if (!userMemory.isEmpty()) {
            system.append("\n\n# 用户长期记忆（来自 ~/.agentx/AGENTX.md，跨会话的偏好与事实）\n")
                    .append(userMemory);
        }
        result.add(new SystemMessage(system.toString()));
        result.addAll(modelMemoryService.loadHistory(conversation.getId()));
        return result;
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
            if (PLAN_TOOL.equals(toolName)) {
                // 计划是交互编排不是事实操作：只回写会话计划态+发帧，不进 blocks
                if (argsJson != null && !argsJson.isBlank()) {
                    try {
                        conversationService.updatePlanState(conversationId, argsJson);
                    } catch (RuntimeException e) {
                        log.warn("计划状态回写失败 conversation={}: {}", conversationId, e.getMessage());
                    }
                }
                sender.send(SseEvent.toolCall(callId, toolName, argsJson, kind, preview));
                return;
            }
            aggregator.recordToolCall(callId, toolName, argsJson, kind, preview);
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
        /** 入忆版用户文本（附件占位/skill 展开后）：与业务轨 content 双轨并存 */
        private final String memoryUserText;
        private final StringBuilder content = new StringBuilder();
        private final BlockAssembler blockAssembler = new BlockAssembler();
        private final long startedAt = System.currentTimeMillis();
        private long promptTokens;
        private long completionTokens;
        private String modelName = "";
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
                         String userContent, String memoryUserText) {
            this.user = user;
            this.conversation = conversation;
            this.assistantMessageId = assistantMessageId;
            this.userContent = userContent;
            this.memoryUserText = memoryUserText;
        }

        void recordToolCall(String callId, String toolName, String argsJson, String kind,
                            Map<String, Object> preview) {
            blockAssembler.recordToolCall(callId, toolName, argsJson, kind, preview);
        }

        void recordToolResult(String callId, String result) {
            blockAssembler.recordToolResult(callId, result);
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
            if (response == null) {
                return;
            }
            // usage/model 必须在 result 判空**之前**提取:OpenAI 协议 include_usage 的
            // usage 随最后一块下发,而该块 choices 为空(getResult()==null)——
            // 放在后面会被提前 return 吞掉,兼容供应商 token 统计恒 0
            if (response.getMetadata() != null) {
                Usage usage = response.getMetadata().getUsage();
                if (usage != null && usage.getTotalTokens() != null && usage.getTotalTokens() > 0) {
                    promptTokens = usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
                    completionTokens = usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
                }
                if (response.getMetadata().getModel() != null && !response.getMetadata().getModel().isBlank()) {
                    modelName = response.getMetadata().getModel();
                }
            }
            if (response.getResult() == null) {
                return;
            }
            var output = response.getResult().getOutput();
            if (output instanceof DeepSeekAssistantMessage dsm && dsm.getReasoningContent() != null
                    && !dsm.getReasoningContent().isEmpty()) {
                blockAssembler.appendReasoning(dsm.getReasoningContent());
                sender.send(SseEvent.reasoning(dsm.getReasoningContent()));
            }
            String delta = output.getText();
            if (delta != null && !delta.isEmpty()) {
                content.append(delta);
                sender.send(SseEvent.textDelta(delta));
            }
        }

        void onComplete(SseEmitterSender sender) {
            try {
                persistAssistantMessage(null);
                recordMemory();
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
                    // 有部分产出的失败轮次照记（与业务轨一致）：重试时上下文不缺前情
                    recordMemory();
                }
                auditor.record(user.id(), conversation.getId(), modelName,
                        promptTokens, completionTokens,
                        System.currentTimeMillis() - startedAt, AiCallAuditor.CallStatus.ERROR);
                sender.send(SseEvent.error("50000", "模型调用失败：" + error.getMessage()));
            } finally {
                sender.complete();
            }
        }

        /**
         * 模型轨回写（原 MessageChatMemoryAdvisor 的 after 职责）：
         * 入忆 user 用占位版、assistant 尾部附工具轨迹摘要；随后按阈值触发滚动压缩。
         * 记忆失败只降级不影响 SSE 收尾。
         */
        private void recordMemory() {
            try {
                List<Map<String, Object>> records = blockAssembler.toolRecords();
                modelMemoryService.recordRound(conversation.getId(), memoryUserText,
                        content.toString(), ToolTraceSummary.of(records));
                modelMemoryService.maybeCompactAsync(conversation.getId(),
                        conversationService.latestPlanState(conversation.getId()));
            } catch (RuntimeException e) {
                log.warn("记忆回写失败 conversation={}: {}", conversation.getId(), e.getMessage());
            }
        }

        private void persistAssistantMessage(String errorNote) {
            ChatMessage assistant = new ChatMessage();
            assistant.setId(assistantMessageId);
            assistant.setConversationId(conversation.getId());
            assistant.setRole(MessageRole.ASSISTANT);
            assistant.setContent(content.toString());
            if (!blockAssembler.isEmpty()) {
                assistant.setBlocks(objectMapper.writeValueAsString(blockAssembler.snapshot()));
            }
            if (!ragSources.isEmpty()) {
                assistant.setRagSources(objectMapper.writeValueAsString(ragSources));
            }
            if (promptTokens > 0 || completionTokens > 0) {
                assistant.setTokenUsage("{\"promptTokens\":%d,\"completionTokens\":%d}"
                        .formatted(promptTokens, completionTokens));
            }
            conversationService.saveMessage(assistant);
            conversationService.touch(conversation.getId());
        }
    }
}

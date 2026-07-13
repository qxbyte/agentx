package com.agentx.infra.ai.sse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SSE 事件信封 —— 前后端流式契约（设计文档 §7）。
 * 所有流式输出统一为 {"type": "...", ...} 的 JSON 帧，前端按 type 分发渲染；
 * 业务错误也走 error 帧而非断流，保证前端状态机始终能收到终止信号。
 * <p>
 * 建模为 type + data 而非巨型多字段 record：新事件类型只加一个静态工厂，
 * 序列化经 {@link #toPayload()} 展平成单层 JSON，字段天然免 null。
 */
public record SseEvent(String type, Map<String, Object> data) {

    public static final String META = "meta";
    public static final String TEXT_DELTA = "text-delta";
    public static final String REASONING = "reasoning";
    public static final String TOOL_CALL = "tool-call";
    public static final String TOOL_RESULT = "tool-result";
    public static final String RAG_SOURCE = "rag-source";
    public static final String APPROVAL_REQUEST = "approval-request";
    public static final String DONE = "done";
    public static final String ERROR = "error";

    /** 展平为待序列化的单层 JSON 结构：{"type": ..., ...data} */
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.putAll(data);
        return payload;
    }

    public static SseEvent meta(String conversationId, String messageId) {
        return new SseEvent(META, Map.of("conversationId", conversationId, "messageId", messageId));
    }

    public static SseEvent textDelta(String delta) {
        return new SseEvent(TEXT_DELTA, Map.of("delta", delta));
    }

    public static SseEvent reasoning(String delta) {
        return new SseEvent(REASONING, Map.of("delta", delta));
    }

    public static SseEvent toolCall(String id, String name, String argsJson) {
        return toolCall(id, name, argsJson, null, null);
    }

    /** 富化版：kind + 结构化预览（CodeAgent），null 字段自动省略。 */
    public static SseEvent toolCall(String id, String name, String argsJson, String kind,
                                    Map<String, Object> preview) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("name", name);
        data.put("args", argsJson);
        if (kind != null) data.put("kind", kind);
        if (preview != null) data.put("preview", preview);
        return new SseEvent(TOOL_CALL, data);
    }

    public static SseEvent toolResult(String id, String name, String result) {
        return toolResult(id, name, result, null);
    }

    /** 富化版：附带 kind 供前端选择渲染器（CodeAgent）。 */
    public static SseEvent toolResult(String id, String name, String result, String kind) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("name", name);
        data.put("result", result);
        if (kind != null) data.put("kind", kind);
        return new SseEvent(TOOL_RESULT, data);
    }

    public static SseEvent ragSource(List<? extends Map<String, Object>> sources) {
        return new SseEvent(RAG_SOURCE, Map.of("sources", sources));
    }

    /** 审批请求帧（CodeAgent Ask 模式）：preview 按 kind 携带 diff/command 等结构化预览。 */
    public static SseEvent approvalRequest(String approvalId, String toolName, String kind,
                                           Map<String, Object> preview) {
        return new SseEvent(APPROVAL_REQUEST, Map.of(
                "approvalId", approvalId, "toolName", toolName, "kind", kind, "preview", preview));
    }

    public static SseEvent done(long promptTokens, long completionTokens, String finishReason) {
        return new SseEvent(DONE, Map.of(
                "usage", Map.of("promptTokens", promptTokens, "completionTokens", completionTokens),
                "finishReason", finishReason == null ? "stop" : finishReason));
    }

    public static SseEvent error(String code, String message) {
        return new SseEvent(ERROR, Map.of("code", code, "message", message));
    }
}

package com.agentx.infra.ai.stream;

import java.util.Map;

/**
 * 工具执行事件的接收端。chat 层实现为 SSE tool-call/tool-result/approval-request 帧；
 * 测试实现为内存收集器。
 */
public interface ToolEventSink {

    void onToolCall(String callId, String toolName, String argsJson);

    void onToolResult(String callId, String toolName, String result);

    /**
     * 富化版 tool-call（CodeAgent）：附带展示类型与结构化预览。默认退化为纯文本版，
     * 由 SSE 实现覆盖以把 kind/preview 带进帧。
     */
    default void onToolCall(String callId, String toolName, String argsJson, String kind,
                            Map<String, Object> preview) {
        onToolCall(callId, toolName, argsJson);
    }

    /** 富化版 tool-result（CodeAgent）：附带展示类型。默认退化为纯文本版。 */
    default void onToolResult(String callId, String toolName, String result, String kind) {
        onToolResult(callId, toolName, result);
    }

    /**
     * 审批请求（CodeAgent Ask 模式）：危险工具执行前发出，前端渲染审批卡。
     *
     * @param approvalId 审批标识，前端据此回传决定
     * @param toolName   工具名
     * @param kind       预览类型：patch / shell / write / commit
     * @param preview    结构化预览（diff / command 等），前端按 kind 分发渲染
     */
    default void onApprovalRequest(String approvalId, String toolName, String kind,
                                   Map<String, Object> preview) {
        // 默认忽略（非 coding 场景不产生审批）
    }

    /**
     * 审批终态（CodeAgent Ask 模式）：审批 future 落定后发出，前端据此翻转审批卡。
     * 无论批准/拒绝/超时都会触发——审批卡终态以此帧为权威，不依赖回传请求自身的响应。
     *
     * @param outcome approved / rejected / expired（超时或会话结束）
     */
    default void onApprovalResult(String approvalId, String outcome) {
        // 默认忽略（非 coding 场景不产生审批）
    }

    ToolEventSink NOOP = new ToolEventSink() {
        @Override
        public void onToolCall(String callId, String toolName, String argsJson) {}

        @Override
        public void onToolResult(String callId, String toolName, String result) {}
    };
}

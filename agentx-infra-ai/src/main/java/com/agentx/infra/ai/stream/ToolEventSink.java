package com.agentx.infra.ai.stream;

/**
 * 工具执行事件的接收端。chat 层实现为 SSE tool-call/tool-result 帧；
 * 测试实现为内存收集器。
 */
public interface ToolEventSink {

    void onToolCall(String callId, String toolName, String argsJson);

    void onToolResult(String callId, String toolName, String result);

    ToolEventSink NOOP = new ToolEventSink() {
        @Override
        public void onToolCall(String callId, String toolName, String argsJson) {}

        @Override
        public void onToolResult(String callId, String toolName, String result) {}
    };
}

package com.agentx.infra.ai.stream;

import java.util.Map;

/**
 * 工具帧富化策略（通用 SPI）：把工具的展示类型与结构化预览注入 tool-call/tool-result 帧，
 * 让前端按类型分发富渲染（diff / 终端 / 命中列表…）而不必猜。
 * <p>
 * infra-ai 只定义契约、不含任何具体工具知识；由领域模块（如 coding）实现并按需注入到
 * {@link SseNotifyingToolCallback}。无实现时帧退化为纯文本（普通对话不受影响）。
 */
public interface ToolPreviewProvider {

    /** 工具的展示类型（patch/shell/write/commit/read/grep…）；未知返回 null。 */
    String kindOf(String toolName);

    /** 由入参构建 tool-call 的结构化预览（diff/command 等）；无则返回 null。 */
    Map<String, Object> previewOf(String toolName, String argsJson);
}

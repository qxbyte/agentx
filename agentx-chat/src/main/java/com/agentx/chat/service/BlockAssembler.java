package com.agentx.chat.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息 blocks 装配器：按 SSE 帧到达顺序把一轮流式应答装配成有序 block 数组
 * （对齐 Claude/Codex 的 transcript 模型，设计文档 §4）。
 * blocks 是消息展示轨的唯一真相源；线程安全（工具回调与模型增量来自不同线程）。
 */
public final class BlockAssembler {

    private final List<Map<String, Object>> blocks = new ArrayList<>();

    /** reasoning 增量：末尾是 reasoning block 就续写，否则新开一段（天然按工具调用分段）。 */
    public synchronized void appendReasoning(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        Map<String, Object> last = blocks.isEmpty() ? null : blocks.get(blocks.size() - 1);
        if (last != null && "reasoning".equals(last.get("type"))) {
            last.put("text", last.get("text") + delta);
            return;
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "reasoning");
        block.put("text", delta);
        blocks.add(block);
    }

    public synchronized void recordToolCall(String id, String name, String args, String kind,
                                            Map<String, Object> preview) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "tool");
        block.put("id", id);
        block.put("name", name);
        block.put("args", args == null ? "" : args);
        if (kind != null) {
            block.put("kind", kind);
        }
        if (preview != null && !preview.isEmpty()) {
            block.put("preview", new LinkedHashMap<>(preview));
        }
        blocks.add(block);
    }

    /** 按 id 回填结果；未知 id 静默忽略（如 updatePlan 未入 blocks 的回填）。 */
    public synchronized void recordToolResult(String id, String result) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            Map<String, Object> b = blocks.get(i);
            if ("tool".equals(b.get("type")) && id != null && id.equals(b.get("id"))) {
                b.put("result", result == null ? "" : result);
                return;
            }
        }
    }

    public synchronized boolean isEmpty() {
        return blocks.isEmpty();
    }

    /** 深拷贝快照：落库序列化用，避免并发修改。 */
    public synchronized List<Map<String, Object>> snapshot() {
        return blocks.stream().map(b -> (Map<String, Object>) new LinkedHashMap<>(b)).toList();
    }

    /** 工具记录（{id,name,args[,result],…}）：ToolTraceSummary 入参形状。 */
    public synchronized List<Map<String, Object>> toolRecords() {
        return blocks.stream().filter(b -> "tool".equals(b.get("type")))
                .map(b -> (Map<String, Object>) new LinkedHashMap<>(b)).toList();
    }
}

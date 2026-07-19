package com.agentx.chat.service.memory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工具轨迹一行摘要：把一轮的 toolCalls 记录压成事实陈述附在记忆里的
 * 助手消息尾部——下一轮模型知道自己做过什么（读过哪些文件、跑过什么命令、
 * 成败如何），避免重复探索。业务轨与用户可见回复不带此摘要。
 * <p>
 * 纯格式化、零模型调用。失败结果照记（保留错误让模型不重蹈覆辙）。
 */
public final class ToolTraceSummary {

    /** 摘要总长上限：防超长参数（如整文件写入）挤占记忆。 */
    private static final int MAX_CHARS = 500;
    private static final int MAX_ARG_CHARS = 60;
    /** 不入摘要的工具：计划/提问是交互编排，不是事实性操作。 */
    private static final java.util.Set<String> EXCLUDED = java.util.Set.of(
            "updatePlan", "askUserQuestion");

    private ToolTraceSummary() {}

    /**
     * @param records StreamAggregator 的记录（{id,name,args[,result]}），顺序即调用顺序
     * @return 一行摘要；无可摘要内容返回空串
     */
    public static String of(List<Map<String, Object>> records) {
        if (records == null || records.isEmpty()) {
            return "";
        }
        String body = records.stream()
                .filter(r -> !EXCLUDED.contains(String.valueOf(r.get("name"))))
                .map(ToolTraceSummary::formatOne)
                .collect(Collectors.joining("；"));
        if (body.isEmpty()) {
            return "";
        }
        String summary = "【本轮工具操作：" + body + "】";
        return summary.length() <= MAX_CHARS ? summary : summary.substring(0, MAX_CHARS - 2) + "…】";
    }

    private static String formatOne(Map<String, Object> record) {
        String name = String.valueOf(record.get("name"));
        String args = clip(String.valueOf(record.getOrDefault("args", "")));
        Object result = record.get("result");
        String outcome = outcomeOf(result == null ? null : String.valueOf(result));
        return name + "(" + args + ")" + outcome;
    }

    /** 结果只记成败信号，不记内容——内容太长且业务轨已有全文。 */
    private static String outcomeOf(String result) {
        if (result == null || result.isBlank()) {
            return "";
        }
        String head = result.stripLeading();
        // 常见失败信号：显式报错前缀 / shell 非零退出
        boolean failed = head.startsWith("拒绝") || head.startsWith("失败") || head.contains("异常")
                || head.startsWith("错误") || (head.startsWith("exit=") && !head.startsWith("exit=0"));
        return failed ? "→失败" : "";
    }

    private static String clip(String args) {
        String flat = args.replace('\n', ' ');
        return flat.length() <= MAX_ARG_CHARS ? flat : flat.substring(0, MAX_ARG_CHARS) + "…";
    }
}

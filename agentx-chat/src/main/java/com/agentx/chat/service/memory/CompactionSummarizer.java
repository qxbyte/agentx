package com.agentx.chat.service.memory;

/**
 * 压缩摘要生成器：把早期对话文本浓缩为交接摘要。
 * 抽象为接口以隔离模型调用——{@link ModelMemoryService} 的压缩编排逻辑
 * 可用确定性替身单测，默认实现走平台默认 CHAT 模型。
 */
@FunctionalInterface
public interface CompactionSummarizer {

    /**
     * @param transcript 待压缩的早期对话文本（已格式化为「角色: 内容」段落）
     * @return 结构化摘要正文
     * @throws RuntimeException 模型调用失败——调用方保持原记忆不动
     */
    String summarize(String transcript);
}

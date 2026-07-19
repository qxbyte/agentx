package com.agentx.chat.service.memory;

import com.agentx.infra.ai.client.ChatClientFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 默认压缩摘要实现：平台默认 CHAT 模型 + 结构化交接提示词
 * （目标/已完成/决策约束/未决/关键文件五个维度）。
 */
@Component
@RequiredArgsConstructor
public class DefaultCompactionSummarizer implements CompactionSummarizer {

    private static final String PROMPT = """
            你在为一段人机对话做上下文压缩。把下面的早期对话浓缩成交接摘要，\
            供模型在后续轮次中作为唯一的早期记忆使用。按以下结构输出：

            1. 任务目标与背景
            2. 已完成的工作（含关键结论）
            3. 重要决策与约束（用户明确的偏好、要求、否决项——逐条保留，不得遗漏）
            4. 未决事项与下一步
            5. 涉及的关键文件/路径/命令/标识符（原样保留，不要改写）

            只输出摘要正文。信息以保真优先，宁可多留不可臆造。

            对话记录：
            %s
            """;

    private final ChatClientFactory chatClientFactory;

    @Override
    public String summarize(String transcript) {
        String result = chatClientFactory.getDefault().prompt()
                .user(PROMPT.formatted(transcript))
                .call()
                .content();
        if (result == null || result.isBlank()) {
            throw new IllegalStateException("压缩摘要为空");
        }
        return result.strip();
    }
}

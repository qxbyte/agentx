package com.agentx.chat.service.memory;

import com.agentx.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 记忆管线：回写语义 / 预算裁剪 / 滚动压缩（advisor 替代层的行为契约）。 */
class ModelMemoryServiceTest {

    private final UUID conversationId = UUID.randomUUID();
    private ChatMemory chatMemory;
    private final AtomicInteger summarizerCalls = new AtomicInteger();
    private ModelMemoryService service;

    @BeforeEach
    void setUp() {
        chatMemory = MessageWindowChatMemory.builder().maxMessages(200).build();
        service = new ModelMemoryService(chatMemory,
                transcript -> {
                    summarizerCalls.incrementAndGet();
                    return "目标：测试。已完成：若干轮对话。";
                });
    }

    @Test
    void recordRoundStoresUserAndAssistantWithToolSummary() {
        service.recordRound(conversationId, "用户提问", "助手回复", "【本轮工具操作：readFile(a.md)】");

        List<Message> messages = chatMemory.get(conversationId.toString());
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getText()).isEqualTo("用户提问");
        assertThat(messages.get(1).getText())
                .isEqualTo("助手回复\n\n【本轮工具操作：readFile(a.md)】");
    }

    @Test
    void emptyRoundIsNotRecorded() {
        // 刻意差异：空轮次（无回复且无工具操作）不写——原 advisor 会写空 assistant
        service.recordRound(conversationId, "用户提问", "", "");
        assertThat(chatMemory.get(conversationId.toString())).isEmpty();
    }

    @Test
    void loadHistoryTrimsOldestWhenOverBudget() {
        // 20 轮巨型消息（每条约 4000 token），远超 32k 预算
        String big = "字".repeat(4000);
        for (int i = 0; i < 20; i++) {
            service.recordRound(conversationId, "问" + i + big, "答" + i + big, "");
        }
        List<Message> loaded = service.loadHistory(conversationId);

        assertThat(TokenEstimator.estimate(loaded))
                .isLessThanOrEqualTo(ModelMemoryService.PROMPT_BUDGET_TOKENS);
        // 从最老开始丢：留下的是最新的轮次
        assertThat(loaded.get(loaded.size() - 1).getText()).startsWith("答19");
        assertThat(loaded.get(0).getText()).doesNotStartWith("问0");
    }

    @Test
    void compactReplacesHeadWithSummaryPairAndKeepsRecent() {
        for (int i = 0; i < 12; i++) {
            service.recordRound(conversationId, "问题" + i, "回答" + i, "");
        }
        var result = service.compact(conversationId, "{\"todos\":[{\"content\":\"步骤A\"}]}");

        assertThat(summarizerCalls.get()).isEqualTo(1);
        List<Message> after = chatMemory.get(conversationId.toString());
        // 摘要对(2) + 保留的最近消息
        assertThat(after).hasSize(2 + ModelMemoryService.KEEP_RECENT_MESSAGES);
        assertThat(after.get(0)).isInstanceOf(UserMessage.class);
        assertThat(after.get(0).getText())
                .startsWith(ModelMemoryService.SUMMARY_MARKER)
                .contains("步骤A"); // 恢复锚点：任务清单原文并入摘要
        assertThat(after.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(after.get(after.size() - 1).getText()).isEqualTo("回答11");
        assertThat(result.compactedMessages()).isEqualTo(24 - ModelMemoryService.KEEP_RECENT_MESSAGES);
    }

    @Test
    void compactOnShortHistoryRejects() {
        service.recordRound(conversationId, "唯一提问", "唯一回答", "");
        assertThatThrownBy(() -> service.compact(conversationId, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无需压缩");
    }

    @Test
    void failedSummarizerLeavesMemoryUntouched() {
        ModelMemoryService failing = new ModelMemoryService(chatMemory, t -> {
            throw new IllegalStateException("模型不可用");
        });
        for (int i = 0; i < 12; i++) {
            failing.recordRound(conversationId, "问" + i, "答" + i, "");
        }
        List<Message> before = chatMemory.get(conversationId.toString());
        assertThatThrownBy(() -> failing.compact(conversationId, null))
                .isInstanceOf(IllegalStateException.class);
        // 摘要失败绝不半途 clear：记忆原样保留
        assertThat(chatMemory.get(conversationId.toString())).hasSize(before.size());
    }

    @Test
    void loadHistoryPreservesLeadingSummaryPairWhenTrimming() {
        for (int i = 0; i < 12; i++) {
            service.recordRound(conversationId, "问" + i, "答" + i, "");
        }
        service.compact(conversationId, null);
        // 压缩后再灌入巨量轮次触发预算裁剪
        String big = "字".repeat(4000);
        for (int i = 0; i < 12; i++) {
            service.recordRound(conversationId, "新问" + i + big, "新答" + i + big, "");
        }
        List<Message> loaded = service.loadHistory(conversationId);
        // 头部摘要对是早期上下文唯一存根：裁剪时必须保留
        assertThat(loaded.get(0).getText()).startsWith(ModelMemoryService.SUMMARY_MARKER);
    }
}

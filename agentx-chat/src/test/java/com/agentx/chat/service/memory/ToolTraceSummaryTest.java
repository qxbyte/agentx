package com.agentx.chat.service.memory;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/** 工具轨迹摘要：格式化、排除交互工具、失败信号、长度上限。 */
class ToolTraceSummaryTest {

    @Test
    void formatsCallsWithFailureSignal() {
        String summary = ToolTraceSummary.of(List.of(
                Map.of("id", "1", "name", "readFile", "args", "{\"path\":\"src/App.tsx\"}",
                        "result", "文件内容…"),
                Map.of("id", "2", "name", "runShell", "args", "{\"command\":\"npm test\"}",
                        "result", "exit=1\nFAIL")));
        assertThat(summary)
                .startsWith("【本轮工具操作：")
                .contains("readFile({\"path\":\"src/App.tsx\"})")
                .contains("runShell({\"command\":\"npm test\"})→失败");
    }

    @Test
    void excludesInteractionToolsAndReturnsEmptyWhenNothingLeft() {
        assertThat(ToolTraceSummary.of(List.of(
                Map.of("id", "1", "name", "updatePlan", "args", "{}"),
                Map.of("id", "2", "name", "askUserQuestion", "args", "{}")))).isEmpty();
        assertThat(ToolTraceSummary.of(List.of())).isEmpty();
    }

    @Test
    void capsTotalAndPerArgLength() {
        String bigArgs = "x".repeat(500);
        String summary = ToolTraceSummary.of(List.of(
                Map.of("id", "1", "name", "writeFile", "args", bigArgs),
                Map.of("id", "2", "name", "writeFile", "args", bigArgs),
                Map.of("id", "3", "name", "writeFile", "args", bigArgs),
                Map.of("id", "4", "name", "writeFile", "args", bigArgs),
                Map.of("id", "5", "name", "writeFile", "args", bigArgs),
                Map.of("id", "6", "name", "writeFile", "args", bigArgs),
                Map.of("id", "7", "name", "writeFile", "args", bigArgs),
                Map.of("id", "8", "name", "writeFile", "args", bigArgs)));
        assertThat(summary.length()).isLessThanOrEqualTo(500);
        assertThat(summary).endsWith("】");
    }
}

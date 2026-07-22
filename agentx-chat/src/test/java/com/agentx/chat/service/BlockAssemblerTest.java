package com.agentx.chat.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/** blocks 装配：reasoning 分段合并、tool 追加与 result 回填、快照与工具记录派生。 */
class BlockAssemblerTest {

    @Test
    void mergesConsecutiveReasoningAndSplitsAroundTools() {
        BlockAssembler a = new BlockAssembler();
        a.appendReasoning("让我看看");
        a.appendReasoning("目录结构");
        a.recordToolCall("t1", "listDir", "{\"path\":\".\"}", "read");
        a.appendReasoning("是前后端两个项目");

        List<Map<String, Object>> blocks = a.snapshot();
        assertThat(blocks).hasSize(3);
        assertThat(blocks.get(0)).containsEntry("type", "reasoning")
                .containsEntry("text", "让我看看目录结构");
        assertThat(blocks.get(1)).containsEntry("type", "tool")
                .containsEntry("id", "t1").containsEntry("name", "listDir")
                .containsEntry("kind", "read");
        assertThat(blocks.get(2)).containsEntry("text", "是前后端两个项目");
    }

    @Test
    void fillsResultByIdAndIgnoresUnknownId() {
        BlockAssembler a = new BlockAssembler();
        a.recordToolCall("t1", "readFile", "{\"path\":\"a\"}", null);
        a.recordToolResult("t1", "内容");
        a.recordToolResult("ghost", "x"); // 未知 id 静默忽略
        assertThat(a.snapshot().get(0)).containsEntry("result", "内容")
                .doesNotContainKey("kind"); // kind 为 null 不落键
    }

    @Test
    void toolRecordsFiltersOnlyToolBlocks() {
        BlockAssembler a = new BlockAssembler();
        a.appendReasoning("想一想");
        a.recordToolCall("t1", "runShell", "{\"command\":\"ls\"}", "shell");
        assertThat(a.toolRecords()).hasSize(1);
        assertThat(a.toolRecords().get(0)).containsEntry("name", "runShell");
        assertThat(a.isEmpty()).isFalse();
        assertThat(new BlockAssembler().isEmpty()).isTrue();
    }
}

package com.agentx.rag.ingest;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverlappingTextSplitterTest {

    @Test
    void shortTextIsSingleChunk() {
        assertThat(new OverlappingTextSplitter(200, 20).split("短文本。"))
                .containsExactly("短文本。");
    }

    @Test
    void splitsAtSentenceBoundary() {
        String sentence = "这是第一句话。".repeat(30); // 210 字符
        List<String> chunks = new OverlappingTextSplitter(100, 20).split(sentence);
        assertThat(chunks.size()).isGreaterThan(1);
        // 每块都应以句号结尾（分隔符优先级生效），末块除外可任意
        for (int i = 0; i < chunks.size() - 1; i++) {
            assertThat(chunks.get(i)).endsWith("。");
        }
    }

    @Test
    void adjacentChunksOverlap() {
        String text = "a".repeat(500);
        List<String> chunks = new OverlappingTextSplitter(200, 50).split(text);
        assertThat(chunks.size()).isGreaterThanOrEqualTo(3);
        // 无分隔符时硬切，块间保留 overlap：块2 开头 = 块1 结尾 50 字符
        String tailOfFirst = chunks.get(0).substring(chunks.get(0).length() - 50);
        assertThat(chunks.get(1)).startsWith(tailOfFirst);
    }

    @Test
    void coversAllContent() {
        String text = "段落一。\n\n段落二有一些更长的内容在这里。\n\n段落三结束。".repeat(20);
        List<String> chunks = new OverlappingTextSplitter(120, 24).split(text);
        String joined = String.join("", chunks);
        // 允许重叠导致的重复，但原文的每个片段必须出现
        assertThat(joined).contains("段落一。").contains("段落二有一些更长的内容在这里。").contains("段落三结束。");
    }

    @Test
    void blankInputYieldsEmpty() {
        assertThat(new OverlappingTextSplitter(200, 20).split("  \n ")).isEmpty();
    }

    @Test
    void invalidParamsRejected() {
        assertThatThrownBy(() -> new OverlappingTextSplitter(32, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OverlappingTextSplitter(200, 200))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

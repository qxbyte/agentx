package com.agentx.rag.ingest;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class MarkdownStructureSplitterTest {

    @Test
    void headingsFormBoundariesAndPrefixChunks() {
        String md = """
                # 架构

                总体分层说明。

                ## 检索层

                向量检索与重排逻辑。
                """;
        List<String> chunks = new MarkdownStructureSplitter(200, 0).split(md);
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).startsWith("架构\n").contains("总体分层说明。");
        // 子标题继承父级形成章节链
        assertThat(chunks.get(1)).startsWith("架构 › 检索层\n").contains("向量检索与重排逻辑。");
    }

    @Test
    void fencedCodeBlockStaysAtomic() {
        // 代码块内含空行与句号，字符窗口切法必然劈开；结构切法必须整体保留
        String code = "```java\nint a = 1;\n\nString s = \"句子。结束。\";\n\nint b = 2;\n```";
        String md = "# 实现\n\n" + code + "\n";
        List<String> chunks = new MarkdownStructureSplitter(64, 0).split(md);
        assertThat(chunks).anySatisfy(c -> assertThat(c).contains(code));
    }

    @Test
    void oversizedTableSplitsByRowRepeatingHeader() {
        StringBuilder md = new StringBuilder("# 清单\n\n| 名称 | 说明 |\n| --- | --- |\n");
        for (int i = 1; i <= 20; i++) {
            md.append("| 条目").append(i).append(" | 这是第").append(i).append("条说明内容 |\n");
        }
        List<String> chunks = new MarkdownStructureSplitter(200, 0).split(md.toString());
        assertThat(chunks.size()).isGreaterThan(1);
        // 每个表格续片都重复表头与对齐分隔行，保证片内表格语义自含
        for (String c : chunks) {
            assertThat(c).contains("| 名称 | 说明 |").contains("| --- | --- |");
        }
        assertThat(String.join("\n", chunks)).contains("| 条目1 ").contains("| 条目20 ");
    }

    @Test
    void frontmatterIsStripped() {
        String md = """
                ---
                title: 秘密标题
                tags: [a, b]
                ---
                # 正文

                正文内容。
                """;
        List<String> chunks = new MarkdownStructureSplitter(200, 0).split(md);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).doesNotContain("秘密标题").contains("正文内容。");
    }

    @Test
    void oversizedParagraphFallsBackToSentenceWindow() {
        String md = "# 长文\n\n" + "这是一句很长的话。".repeat(40);
        List<String> chunks = new MarkdownStructureSplitter(100, 20).split(md);
        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c).startsWith("长文\n"));
    }

    @Test
    void adjacentChunksCarryTailOverlap() {
        String md = "# 甲\n\n第一段内容第一段内容。\n\n# 乙\n\n第二段内容第二段内容。";
        List<String> chunks = new MarkdownStructureSplitter(64, 10).split(md);
        assertThat(chunks).hasSize(2);
        String tail = chunks.get(0).substring(chunks.get(0).length() - 10);
        assertThat(chunks.get(1)).contains("…" + tail);
    }

    @Test
    void blankInputYieldsEmpty() {
        assertThat(new MarkdownStructureSplitter(200, 20).split("  \n ")).isEmpty();
    }
}

package com.agentx.rag.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 结构感知切片器（移植 notopolis chunker 思路，见其 rag/chunker.ts）：
 * <ul>
 *   <li>标题分段：# 标题行切换章节链并强制封片；章节链以「A › B」上下文行置于片首，
 *       提升短片段的语义可检索性（等价 notopolis 的 embedInput 前缀）；</li>
 *   <li>围栏原子：``` / ~~~ 代码块整体成块，超长也不拆——拆开的半个代码块对检索是噪声；</li>
 *   <li>表格原子：连续 | 行整体成块；超长表按行拆分，<b>续片重复表头（表头 + 分隔行）</b>，
 *       保证每片表格语义自含；</li>
 *   <li>frontmatter 剥离；其余按空行分段，同章节内累积到 chunkSize 封片；</li>
 *   <li>相邻片保留尾部 overlap 重叠；超长纯文本段退回 {@link OverlappingTextSplitter}
 *       的句读窗口切分。</li>
 * </ul>
 */
public class MarkdownStructureSplitter implements TextSplitter {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern FENCE = Pattern.compile("^\\s*(```|~~~).*$");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\s*\\|.*$");
    /** 表格第二行的对齐分隔行，如 | --- | :---: | */
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\s*\\|?[\\s:|\\-]+\\|?\\s*$");

    private final int chunkSize;
    private final int overlap;
    private final OverlappingTextSplitter plainFallback;

    public MarkdownStructureSplitter(int chunkSize, int overlap) {
        // 参数校验复用通用切片器的约束（chunkSize>=64、overlap∈[0,chunkSize)）
        this.plainFallback = new OverlappingTextSplitter(chunkSize, overlap);
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    private record Heading(int level, String text) {}

    /** 原子块：文本 + 所属章节链。围栏/表格在成块阶段已保证自含，累积阶段不再拆。 */
    private record Block(String text, List<String> headings) {}

    @Override
    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String content = stripFrontmatter(text.replace("\r\n", "\n").replace('\r', '\n'));
        List<Block> blocks = splitBlocks(content);

        List<Block> cur = new ArrayList<>();
        int curLen = 0;
        for (Block b : blocks) {
            boolean headingChanged = !cur.isEmpty() && !cur.get(0).headings().equals(b.headings());
            if (headingChanged || (curLen > 0 && curLen + b.text().length() > chunkSize)) {
                seal(chunks, cur);
                curLen = 0;
            }
            cur.add(b);
            curLen += b.text().length() + 2;
            if (curLen >= chunkSize) {
                seal(chunks, cur);
                curLen = 0;
            }
        }
        seal(chunks, cur);
        return chunks;
    }

    /** 封片：章节链上下文行 + 上一片尾部重叠 + 块正文（空行拼接）。 */
    private void seal(List<String> chunks, List<Block> cur) {
        if (cur.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        List<String> headings = cur.get(0).headings();
        if (!headings.isEmpty()) {
            sb.append(String.join(" › ", headings)).append('\n');
        }
        if (!chunks.isEmpty() && overlap > 0) {
            String prev = chunks.get(chunks.size() - 1);
            String tail = prev.substring(Math.max(0, prev.length() - overlap));
            if (!tail.isBlank()) {
                sb.append('…').append(tail).append("\n\n");
            }
        }
        sb.append(cur.stream().map(Block::text).reduce((a, b) -> a + "\n\n" + b).orElse(""));
        chunks.add(sb.toString());
        cur.clear();
    }

    /** 把正文拆成原子块：标题行切换章节链；围栏/表格整体成块；其余按空行分段。 */
    private List<Block> splitBlocks(String content) {
        String[] lines = content.split("\n", -1);
        List<Block> blocks = new ArrayList<>();
        List<Heading> stack = new ArrayList<>();
        List<String> para = new ArrayList<>();
        List<String> fence = new ArrayList<>();
        List<String> table = new ArrayList<>();
        boolean inFence = false;

        for (String line : lines) {
            if (inFence) {
                fence.add(line);
                if (FENCE.matcher(line).matches()) {
                    blocks.add(new Block(String.join("\n", fence), chainOf(stack)));
                    fence.clear();
                    inFence = false;
                }
                continue;
            }
            if (FENCE.matcher(line).matches()) {
                flushPara(para, blocks, stack);
                flushTable(table, blocks, stack);
                inFence = true;
                fence.add(line);
                continue;
            }
            if (TABLE_ROW.matcher(line).matches()) {
                flushPara(para, blocks, stack);
                table.add(line);
                continue;
            }
            flushTable(table, blocks, stack);
            Matcher hm = HEADING.matcher(line);
            if (hm.matches()) {
                flushPara(para, blocks, stack);
                int level = hm.group(1).length();
                while (!stack.isEmpty() && stack.get(stack.size() - 1).level() >= level) {
                    stack.remove(stack.size() - 1);
                }
                stack.add(new Heading(level, hm.group(2).strip()));
                continue;
            }
            if (line.isBlank()) {
                flushPara(para, blocks, stack);
                continue;
            }
            para.add(line);
        }
        // EOF：未闭合围栏同样原子成块
        if (!fence.isEmpty()) {
            blocks.add(new Block(String.join("\n", fence), chainOf(stack)));
        }
        flushTable(table, blocks, stack);
        flushPara(para, blocks, stack);
        return blocks;
    }

    /** 普通段落成块；超长段（如 Tika 拍平的无空行长文）退回句读窗口切分。 */
    private void flushPara(List<String> para, List<Block> blocks, List<Heading> stack) {
        if (para.isEmpty()) {
            return;
        }
        String text = String.join("\n", para).strip();
        para.clear();
        if (text.isEmpty()) {
            return;
        }
        List<String> chain = chainOf(stack);
        if (text.length() > chunkSize) {
            plainFallback.split(text).forEach(piece -> blocks.add(new Block(piece, chain)));
        } else {
            blocks.add(new Block(text, chain));
        }
    }

    /** 表格成块；超长表按行拆分，续片重复表头（表头 + 对齐分隔行）。 */
    private void flushTable(List<String> table, List<Block> blocks, List<Heading> stack) {
        if (table.isEmpty()) {
            return;
        }
        List<String> rows = new ArrayList<>(table);
        table.clear();
        List<String> chain = chainOf(stack);
        String whole = String.join("\n", rows);
        if (whole.length() <= chunkSize) {
            blocks.add(new Block(whole, chain));
            return;
        }
        int headerRows = rows.size() >= 2 && TABLE_SEPARATOR.matcher(rows.get(1)).matches() ? 2 : 1;
        List<String> header = rows.subList(0, headerRows);
        int headerLen = header.stream().mapToInt(r -> r.length() + 1).sum();
        List<String> piece = new ArrayList<>(header);
        int len = headerLen;
        for (int i = headerRows; i < rows.size(); i++) {
            String row = rows.get(i);
            if (piece.size() > header.size() && len + row.length() > chunkSize) {
                blocks.add(new Block(String.join("\n", piece), chain));
                piece = new ArrayList<>(header);
                len = headerLen;
            }
            piece.add(row);
            len += row.length() + 1;
        }
        if (piece.size() > header.size()) {
            blocks.add(new Block(String.join("\n", piece), chain));
        }
    }

    private static List<String> chainOf(List<Heading> stack) {
        return stack.stream().map(Heading::text).toList();
    }

    /** 剥离文首 YAML frontmatter（--- 包围块）——对嵌入是纯噪声，不进正文。 */
    private static String stripFrontmatter(String text) {
        String[] lines = text.split("\n", -1);
        if (lines.length == 0 || !lines[0].strip().equals("---")) {
            return text;
        }
        for (int i = 1; i < lines.length; i++) {
            String s = lines[i].strip();
            if (s.equals("---") || s.equals("...")) {
                return String.join("\n", List.of(lines).subList(i + 1, lines.length));
            }
        }
        return text;
    }
}

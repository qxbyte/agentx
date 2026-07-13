package com.agentx.rag.ingest;

import java.util.ArrayList;
import java.util.List;

/**
 * 带重叠的文本分段器（设计文档 §4.7）——弥补官方 TokenTextSplitter 无 overlap、
 * 无分隔符优先级的短板。
 * <p>
 * 策略：目标块长 chunkSize（字符），在 [chunkSize-overlap, chunkSize] 窗口内
 * 按分隔符优先级（段落 > 换行 > 中英句号/问叹号 > 空格）寻找最佳切点；
 * 相邻块保留 overlap 字符重叠，保证跨块语义连续。
 */
public class OverlappingTextSplitter implements TextSplitter {

    private static final String[] SEPARATOR_PRIORITY = {"\n\n", "\n", "。", "．", ".", "！", "!", "？", "?", "；", ";", " "};

    private final int chunkSize;
    private final int overlap;

    public OverlappingTextSplitter(int chunkSize, int overlap) {
        if (chunkSize < 64) {
            throw new IllegalArgumentException("chunkSize 不得小于 64");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap 必须在 [0, chunkSize) 内");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String normalized = text.strip();
        int start = 0;
        while (start < normalized.length()) {
            int hardEnd = Math.min(start + chunkSize, normalized.length());
            int end = hardEnd == normalized.length() ? hardEnd : findBreakPoint(normalized, start, hardEnd);
            String chunk = normalized.substring(start, end).strip();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }

    /** 在 [hardEnd-overlap, hardEnd] 窗口内按分隔符优先级找切点（切在分隔符之后）。 */
    private int findBreakPoint(String text, int start, int hardEnd) {
        int windowStart = Math.max(start + 1, hardEnd - Math.max(overlap, chunkSize / 10));
        for (String sep : SEPARATOR_PRIORITY) {
            int idx = text.lastIndexOf(sep, hardEnd - 1);
            if (idx >= windowStart) {
                return idx + sep.length();
            }
        }
        return hardEnd;
    }
}

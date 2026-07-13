package com.agentx.coding.patch;

import java.util.ArrayList;
import java.util.List;

/**
 * 极简 unified diff 解析与应用（设计文档 §4 applyPatch）。
 * 支持标准 --- / +++ / @@ -a,b +c,d @@ 多文件多 hunk。
 * 应用采用"按 hunk 上下文定位 + 就地替换"，上下文不匹配则抛 IllegalStateException。
 */
public final class UnifiedDiff {

    private UnifiedDiff() {}

    public record Hunk(int oldStart, List<String> lines) {}

    public record FilePatch(String path, List<Hunk> hunks, int added, int removed) {}

    public static List<FilePatch> parse(String diff) {
        if (diff == null || diff.isBlank()) {
            return List.of();
        }
        List<FilePatch> files = new ArrayList<>();
        String[] rows = diff.split("\n", -1);
        String path = null;
        List<Hunk> hunks = new ArrayList<>();
        List<String> hunkLines = null;
        int oldStart = 0;
        int added = 0;
        int removed = 0;

        for (String row : rows) {
            if (row.startsWith("--- ")) {
                // 新文件开始，先收尾上一个
                if (path != null) {
                    flushHunk(hunks, hunkLines, oldStart);
                    files.add(new FilePatch(path, List.copyOf(hunks), added, removed));
                    hunks = new ArrayList<>();
                    hunkLines = null;
                    added = removed = 0;
                }
            } else if (row.startsWith("+++ ")) {
                path = stripPrefix(row.substring(4).trim());
            } else if (row.startsWith("@@")) {
                flushHunk(hunks, hunkLines, oldStart);
                oldStart = parseOldStart(row);
                hunkLines = new ArrayList<>();
            } else if (hunkLines != null) {
                if (row.startsWith("+")) {
                    added++;
                    hunkLines.add(row);
                } else if (row.startsWith("-")) {
                    removed++;
                    hunkLines.add(row);
                } else if (row.startsWith(" ")) {
                    hunkLines.add(row);
                }
                // 空行与其它行（如 \ No newline、diff 末尾 artifact）忽略
                // 注：真实的空上下文行在 unified diff 中表示为单个空格 " "，已被上一分支捕获
            }
        }
        if (path != null) {
            flushHunk(hunks, hunkLines, oldStart);
            files.add(new FilePatch(path, List.copyOf(hunks), added, removed));
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException("未找到有效的文件补丁头（--- / +++）");
        }
        return files;
    }

    public static List<String> apply(List<String> original, FilePatch patch) {
        List<String> result = new ArrayList<>(original);
        // 从后往前应用 hunk，避免前面的增删改变后面 hunk 的行号
        List<Hunk> ordered = new ArrayList<>(patch.hunks());
        ordered.sort((a, b) -> Integer.compare(b.oldStart(), a.oldStart()));
        for (Hunk hunk : ordered) {
            applyHunk(result, hunk);
        }
        return result;
    }

    private static void applyHunk(List<String> lines, Hunk hunk) {
        int cursor = Math.max(0, hunk.oldStart() - 1);
        List<String> rebuilt = new ArrayList<>();
        int idx = cursor;
        for (String line : hunk.lines()) {
            char tag = line.isEmpty() ? ' ' : line.charAt(0);
            String text = line.length() > 0 ? line.substring(1) : "";
            switch (tag) {
                case ' ' -> {
                    verifyContext(lines, idx, text);
                    rebuilt.add(lines.get(idx));
                    idx++;
                }
                case '-' -> {
                    verifyContext(lines, idx, text);
                    idx++; // 删除：跳过原行
                }
                case '+' -> rebuilt.add(text); // 新增
                default -> { /* ignore */ }
            }
        }
        // 用 rebuilt 替换 [cursor, idx) 区间
        for (int i = idx - 1; i >= cursor; i--) {
            lines.remove(i);
        }
        lines.addAll(cursor, rebuilt);
    }

    private static void verifyContext(List<String> lines, int idx, String expected) {
        if (idx >= lines.size() || !lines.get(idx).equals(expected)) {
            throw new IllegalStateException(
                    "第 " + (idx + 1) + " 行上下文不匹配（期望: " + expected + "）");
        }
    }

    private static void flushHunk(List<Hunk> hunks, List<String> hunkLines, int oldStart) {
        if (hunkLines != null && !hunkLines.isEmpty()) {
            hunks.add(new Hunk(oldStart, List.copyOf(hunkLines)));
        }
    }

    private static int parseOldStart(String header) {
        // @@ -oldStart,oldCount +newStart,newCount @@
        int minus = header.indexOf('-');
        int comma = header.indexOf(',', minus);
        int space = header.indexOf(' ', minus);
        int end = comma > 0 && comma < space ? comma : space;
        try {
            return Integer.parseInt(header.substring(minus + 1, end).trim());
        } catch (RuntimeException e) {
            return 1;
        }
    }

    private static String stripPrefix(String path) {
        if (path.startsWith("a/") || path.startsWith("b/")) {
            return path.substring(2);
        }
        return path;
    }
}

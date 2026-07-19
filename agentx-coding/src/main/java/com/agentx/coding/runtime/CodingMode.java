package com.agentx.coding.runtime;

/** CodeAgent 执行模式（设计文档 §5）。 */
public enum CodingMode {
    /** 只读规划：写/执行工具不注册，物理上不可改动。 */
    PLAN,
    /** 逐操作审批：危险工具执行前需人工确认。 */
    ASK,
    /** 无需审批：全套工具直接执行（工作区边界与命令黑名单仍生效）。 */
    AUTO,
    /**
     * 完全放行：无审批、无工作区路径边界、
     * 命令黑名单仅保留毁机级保护（rm -rf / 、fork bomb、mkfs、dd 写盘、shutdown）。
     */
    BYPASS;

    /** 宽容解析：空/非法一律回退 ASK（最安全的默认）。 */
    public static CodingMode parseOrDefault(String raw) {
        if (raw == null || raw.isBlank()) {
            return ASK;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ASK;
        }
    }
}

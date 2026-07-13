package com.agentx.coding.runtime;

/** CodeAgent 执行模式（设计文档 §5）。 */
public enum CodingMode {
    /** 只读规划：写/执行工具不注册，物理上不可改动。 */
    PLAN,
    /** 逐操作审批：危险工具执行前需人工确认。 */
    ASK,
    /** 无需审批：全套工具直接执行。 */
    AUTO
}

package com.agentx.coding.tools;

import java.util.Set;

/**
 * 危险工具名单（设计文档 §4/§5）：这些工具在 ASK 模式被审批网关拦截，
 * 在 PLAN 模式根本不注册。集中一处，供 CodingStreamCustomizer 与审批网关共用。
 */
public final class DangerousTools {

    public static final Set<String> NAMES = Set.of(
            "writeFile", "applyPatch", "runShell", "gitCommit");

    private DangerousTools() {}

    public static boolean isDangerous(String toolName) {
        return NAMES.contains(toolName);
    }
}

package com.agentx.tools.builtin;

import com.agentx.tools.registry.AgentTool;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内置示例：只读 SQL 查询。演示企业数据接入范式的三道防线——
 * 表白名单、语句形态校验、行数上限；以及经 ToolContext 获取调用者身份。
 * 生产建议再叠加只读数据库账号。
 */
@AgentTool(group = "builtin")
@RequiredArgsConstructor
public class SqlQueryTools {

    /** 允许模型查询的表白名单——企业按需扩充。 */
    private static final Set<String> TABLE_WHITELIST = Set.of("ai_call_log");
    private static final int MAX_ROWS = 20;

    private final JdbcTemplate jdbcTemplate;

    @Tool(description = "对平台数据表执行只读 SELECT 查询（仅白名单表：ai_call_log），返回最多 20 行结果")
    public String query(
            @ToolParam(description = "单条 SELECT 语句，不带分号") String sql,
            ToolContext toolContext) {
        String normalized = sql.strip().toLowerCase();
        if (!normalized.startsWith("select") || normalized.contains(";")) {
            return "拒绝执行：只允许单条 SELECT 语句";
        }
        boolean hitWhitelist = TABLE_WHITELIST.stream().anyMatch(normalized::contains);
        if (!hitWhitelist) {
            return "拒绝执行：目标表不在白名单内（可用表：" + TABLE_WHITELIST + "）";
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql + " LIMIT " + MAX_ROWS);
        return "调用者=" + toolContext.getContext().getOrDefault("userId", "unknown")
                + "，共 " + rows.size() + " 行：" + rows;
    }
}

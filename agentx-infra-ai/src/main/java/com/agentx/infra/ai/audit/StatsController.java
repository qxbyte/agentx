package com.agentx.infra.ai.audit;

import com.agentx.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

/** token 用量统计（ADMIN）：ai_call_log 聚合，管理后台报表数据源。 */
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
public class StatsController {

    private final JdbcTemplate jdbcTemplate;

    /** 总量卡片：调用次数 / prompt / completion tokens / 错误数。 */
    @GetMapping("/tokens/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.ok(jdbcTemplate.queryForMap("""
                SELECT COUNT(*)                            AS total_calls,
                       COALESCE(SUM(prompt_tokens), 0)     AS prompt_tokens,
                       COALESCE(SUM(completion_tokens), 0) AS completion_tokens,
                       COUNT(*) FILTER (WHERE status = 'ERROR') AS error_calls
                FROM ai_call_log"""));
    }

    /** 按日趋势（近 N 天）。 */
    @GetMapping("/tokens/daily")
    public ApiResponse<List<Map<String, Object>>> daily(
            @RequestParam(defaultValue = "14") int days) {
        return ApiResponse.ok(jdbcTemplate.queryForList("""
                SELECT date_trunc('day', created_at)::date  AS day,
                       COUNT(*)                             AS calls,
                       COALESCE(SUM(prompt_tokens), 0)      AS prompt_tokens,
                       COALESCE(SUM(completion_tokens), 0)  AS completion_tokens
                FROM ai_call_log
                WHERE created_at >= now() - make_interval(days => ?)
                GROUP BY 1 ORDER BY 1""", Math.min(Math.max(days, 1), 90)));
    }

    /** 按模型分布。 */
    @GetMapping("/tokens/by-model")
    public ApiResponse<List<Map<String, Object>>> byModel() {
        return ApiResponse.ok(jdbcTemplate.queryForList("""
                SELECT COALESCE(NULLIF(model_name, ''), 'unknown') AS model,
                       COUNT(*)                                    AS calls,
                       COALESCE(SUM(prompt_tokens + completion_tokens), 0) AS total_tokens
                FROM ai_call_log
                GROUP BY 1 ORDER BY total_tokens DESC"""));
    }
}

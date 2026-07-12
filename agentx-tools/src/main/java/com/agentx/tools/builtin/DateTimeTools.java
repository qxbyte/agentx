package com.agentx.tools.builtin;

import com.agentx.tools.registry.AgentTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 内置示例：日期时间工具。演示最小 @Tool 声明与 returnDirect
 * （结果绕过模型直接返回调用方，适合无需模型加工的确定性数据）。
 */
@AgentTool(group = "builtin")
public class DateTimeTools {

    @Tool(description = "获取指定时区的当前日期时间，返回 yyyy-MM-dd HH:mm:ss 格式")
    public String currentDateTime(
            @ToolParam(description = "IANA 时区标识，如 Asia/Shanghai；缺省用系统时区", required = false)
            String zoneId) {
        ZoneId zone = zoneId == null || zoneId.isBlank() ? ZoneId.systemDefault() : ZoneId.of(zoneId);
        return LocalDateTime.now(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Tool(description = "计算两个日期之间相差的天数，日期格式 yyyy-MM-dd", returnDirect = false)
    public long daysBetween(
            @ToolParam(description = "起始日期，yyyy-MM-dd") String from,
            @ToolParam(description = "结束日期，yyyy-MM-dd") String to) {
        return java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.parse(from), java.time.LocalDate.parse(to));
    }
}

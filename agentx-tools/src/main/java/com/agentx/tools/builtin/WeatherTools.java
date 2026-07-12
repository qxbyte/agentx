package com.agentx.tools.builtin;

import com.agentx.tools.registry.AgentTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.util.Map;

/**
 * 内置示例：天气查询。演示"外部 API 型工具"的标准形态——
 * 真实企业接入时把 mock 数据换成 RestClient 调用即可（结构不变）。
 */
@AgentTool(group = "builtin")
public class WeatherTools {

    private static final Map<String, String> MOCK = Map.of(
            "北京", "晴，26°C，西北风 2 级",
            "上海", "多云转小雨，29°C，湿度 78%",
            "深圳", "雷阵雨，31°C，注意防雷");

    @Tool(description = "查询指定城市的当前天气")
    public String currentWeather(@ToolParam(description = "城市名，如 北京") String city) {
        return MOCK.getOrDefault(city, city + "：晴，25°C（示例数据，接入真实气象 API 后返回实时值）");
    }
}

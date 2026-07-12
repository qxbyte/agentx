package com.agentx.mcpserver.demo;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/**
 * @McpTool 示例：模拟订单系统。企业包装存量系统时，把 mock 数据换成
 * 对内部服务的 RestClient/RPC 调用即可，注解与结构不变。
 */
@Service
public class OrderTools {

    private static final Map<String, Map<String, Object>> ORDERS = Map.of(
            "SO-1001", Map.of("orderNo", "SO-1001", "status", "已发货",
                    "amount", 1299.00, "items", List.of("机械键盘")),
            "SO-1002", Map.of("orderNo", "SO-1002", "status", "待支付",
                    "amount", 4599.00, "items", List.of("显示器", "支架")));

    @McpTool(name = "queryOrder", description = "按订单号查询订单状态、金额与商品明细")
    public Map<String, Object> queryOrder(String orderNo) {
        return ORDERS.getOrDefault(orderNo,
                Map.of("orderNo", orderNo, "status", "订单不存在"));
    }

    @McpTool(name = "listRecentOrders", description = "列出最近的订单号")
    public List<String> listRecentOrders() {
        return List.copyOf(ORDERS.keySet());
    }
}

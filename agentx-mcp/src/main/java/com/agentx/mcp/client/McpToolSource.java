package com.agentx.mcp.client;

import com.agentx.tools.registry.ToolSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * L2 MCP 工具来源：把所有健康 MCP 连接的远程工具注入 ToolRegistry。
 * 单连接取列表失败只降级该来源（告警跳过），符合注册中心的降级语义。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolSource implements ToolSource {

    private final McpConnectionManager connectionManager;

    @Override
    public String origin() {
        return "MCP";
    }

    @Override
    public List<ToolCallback> tools() {
        List<ToolCallback> result = new ArrayList<>();
        connectionManager.activeConnections().forEach((id, client) -> {
            try {
                result.addAll(List.of(
                        new SyncMcpToolCallbackProvider(client).getToolCallbacks()));
            } catch (Exception e) {
                log.warn("MCP 工具列表获取失败 connection={}: {}", id, e.getMessage());
            }
        });
        return result;
    }
}

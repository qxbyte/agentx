package com.agentx.mcp.client;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.mcp.domain.McpServerConfig;
import com.agentx.mcp.domain.McpServerConfigRepository;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 连接生命周期管理（设计文档 §4.8 client 侧）。
 * 配置表驱动的动态连接——不走 starter 静态配置，支持运行时增删；
 * 单个连接失败只降级该来源，不影响平台。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpConnectionManager {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final McpServerConfigRepository repository;
    private final ObjectMapper objectMapper;
    private final Map<UUID, McpSyncClient> connections = new ConcurrentHashMap<>();

    /** 启动后异步连接全部启用的 MCP server（失败仅告警）。 */
    @EventListener(ApplicationReadyEvent.class)
    public void connectAllEnabled() {
        repository.findByEnabledTrue().forEach(config ->
                Thread.startVirtualThread(() -> {
                    try {
                        connect(config);
                    } catch (Exception e) {
                        log.warn("MCP 启动连接失败 [{}]: {}", config.getName(), e.getMessage());
                    }
                }));
    }

    public synchronized McpSyncClient connect(McpServerConfig config) {
        disconnect(config.getId());
        McpSyncClient client = buildClient(config);
        client.initialize();
        connections.put(config.getId(), client);
        config.setLastHealthAt(Instant.now());
        repository.save(config);
        log.info("MCP 已连接 [{}] transport={}", config.getName(), config.getTransport());
        return client;
    }

    public synchronized void disconnect(UUID configId) {
        McpSyncClient existing = connections.remove(configId);
        if (existing != null) {
            try {
                existing.closeGracefully();
            } catch (Exception e) {
                log.debug("MCP 关闭异常（忽略）: {}", e.getMessage());
            }
        }
    }

    /** 当前健康连接的快照（McpToolSource 消费）。 */
    public Map<UUID, McpSyncClient> activeConnections() {
        return Map.copyOf(connections);
    }

    public McpSyncClient require(UUID configId) {
        McpSyncClient client = connections.get(configId);
        if (client == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "MCP 未连接");
        }
        return client;
    }

    /** 用临时连接做连通性测试：initialize + listTools 预览，用完即关。 */
    public McpSchema.ListToolsResult testConnection(McpServerConfig config) {
        McpSyncClient client = buildClient(config);
        try {
            client.initialize();
            McpSchema.ListToolsResult tools = client.listTools();
            config.setLastHealthAt(Instant.now());
            repository.save(config);
            return tools;
        } finally {
            try {
                client.closeGracefully();
            } catch (Exception ignored) {
                // 临时连接，关闭失败无影响
            }
        }
    }

    private McpSyncClient buildClient(McpServerConfig config) {
        Map<String, Object> params = objectMapper.readValue(config.getConnectParams(),
                new TypeReference<Map<String, Object>>() {});
        McpClientTransport transport = switch (config.getTransport()) {
            case STDIO -> stdioTransport(params);
            case STREAMABLE_HTTP -> httpTransport(params);
        };
        return McpClient.sync(transport)
                .requestTimeout(REQUEST_TIMEOUT)
                .clientInfo(new McpSchema.Implementation("agentx", "0.1.0"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private McpClientTransport stdioTransport(Map<String, Object> params) {
        String command = (String) params.get("command");
        if (command == null || command.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "STDIO 连接必须提供 command");
        }
        ServerParameters.Builder builder = ServerParameters.builder(command);
        Object args = params.get("args");
        if (args instanceof List<?> list) {
            builder.args(list.stream().map(String::valueOf).toList());
        }
        Object env = params.get("env");
        if (env instanceof Map<?, ?> map) {
            builder.env((Map<String, String>) map);
        }
        return new StdioClientTransport(builder.build(),
                new JacksonMcpJsonMapper(tools.jackson.databind.json.JsonMapper.builder().build()));
    }

    private McpClientTransport httpTransport(Map<String, Object> params) {
        String url = (String) params.get("url");
        if (url == null || url.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "STREAMABLE_HTTP 连接必须提供 url");
        }
        return HttpClientStreamableHttpTransport.builder(url).build();
    }

    @PreDestroy
    void shutdown() {
        connections.keySet().forEach(this::disconnect);
    }
}

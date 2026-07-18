package com.agentx.mcp.service;

import com.agentx.common.util.UuidV7;
import com.agentx.mcp.client.McpConnectionManager;
import com.agentx.mcp.domain.McpServerConfig;
import com.agentx.mcp.domain.McpServerConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 插件 MCP 注册面（plugin 模块调用）：把插件 .mcp.json 声明的服务器同步为
 * MCP 配置（source=PLUGIN,name 带 "plugin:" 命名空间前缀）。
 * <p>
 * 信任边界：MCP server 会启动外部进程/建立外联,安装后一律 **默认停用**,
 * 需用户在 MCP 管理页显式启用;插件更新时保留用户已设置的启停状态;
 * 插件停用/卸载则强制断连并停用/删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginMcpRegistry {

    /** 插件 MCP server 的最小规格（name 已含命名空间前缀）。 */
    public record PluginMcpSpec(String name, McpServerConfig.Transport transport, String connectParams) {}

    private final McpServerConfigRepository repository;
    private final McpConnectionManager connectionManager;

    /** 全量同步:upsert 保留既有启停,新增项默认停用,清理已移除项。 */
    @Transactional
    public void sync(String pluginId, List<PluginMcpSpec> specs) {
        Set<String> names = specs.stream().map(PluginMcpSpec::name).collect(Collectors.toSet());
        for (McpServerConfig stale : repository.findByPluginId(pluginId)) {
            if (!names.contains(stale.getName())) {
                connectionManager.disconnect(stale.getId());
                repository.delete(stale);
            }
        }
        for (PluginMcpSpec spec : specs) {
            var existing = repository.findByName(spec.name());
            if (existing.isPresent() && !"PLUGIN".equals(existing.get().getSource())) {
                log.warn("插件 MCP {} 与用户配置同名,跳过同步", spec.name());
                continue;
            }
            McpServerConfig config = existing.orElseGet(() -> {
                McpServerConfig c = new McpServerConfig();
                c.setId(UuidV7.next());
                c.setName(spec.name());
                c.setEnabled(false);
                return c;
            });
            config.setSource("PLUGIN");
            config.setPluginId(pluginId);
            config.setTransport(spec.transport());
            config.setConnectParams(spec.connectParams());
            repository.save(config);
        }
        if (!specs.isEmpty()) {
            log.info("插件 MCP 已同步 plugin={} count={}（默认停用,需显式启用）", pluginId, specs.size());
        }
    }

    /** 插件停用 → 强制停用并断连其全部 MCP server（重新启用插件不自动恢复,需用户再开）。 */
    @Transactional
    public void disableAll(String pluginId) {
        for (McpServerConfig config : repository.findByPluginId(pluginId)) {
            if (config.isEnabled()) {
                config.setEnabled(false);
                repository.save(config);
            }
            connectionManager.disconnect(config.getId());
        }
    }

    /** 插件卸载 → 断连并删除其全部 MCP server 配置。 */
    @Transactional
    public void remove(String pluginId) {
        for (McpServerConfig config : repository.findByPluginId(pluginId)) {
            connectionManager.disconnect(config.getId());
            repository.delete(config);
        }
    }
}

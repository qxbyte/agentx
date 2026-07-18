package com.agentx.mcp.service;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.common.util.UuidV7;
import com.agentx.mcp.client.McpConnectionManager;
import com.agentx.mcp.domain.McpServerConfig;
import com.agentx.mcp.domain.McpServerConfigRepository;
import com.agentx.mcp.web.dto.McpDtos.UpsertRequest;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class McpServerService {

    private final McpServerConfigRepository repository;
    private final McpConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    public List<McpServerConfig> list() {
        return repository.findAll();
    }

    @Transactional
    public McpServerConfig create(UpsertRequest req) {
        repository.findByName(req.name()).ifPresent(c -> {
            throw new BizException(ErrorCode.CONFLICT, "同名 MCP 配置已存在");
        });
        McpServerConfig config = new McpServerConfig();
        config.setId(UuidV7.next());
        apply(config, req);
        return repository.save(config);
    }

    @Transactional
    public McpServerConfig update(UUID id, UpsertRequest req) {
        McpServerConfig config = get(id);
        boolean wasEnabled = config.isEnabled();
        if ("PLUGIN".equals(config.getSource())) {
            // 插件贡献的配置参数只读(由插件文件维护),仅允许用户切换启停(信任边界开关)
            config.setEnabled(req.enabled());
        } else {
            apply(config, req);
        }
        McpServerConfig saved = repository.save(config);
        // 连接生命周期跟随启用状态
        if (wasEnabled && !saved.isEnabled()) {
            connectionManager.disconnect(id);
        } else if (saved.isEnabled()) {
            connectionManager.connect(saved);
        }
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        McpServerConfig config = get(id);
        if ("PLUGIN".equals(config.getSource())) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "该 MCP 配置由插件「" + config.getPluginId() + "」提供,请通过卸载插件移除");
        }
        connectionManager.disconnect(id);
        repository.delete(config);
    }

    /** 测试连通性：临时连接 initialize + listTools（不改变常驻连接）。 */
    public McpSchema.ListToolsResult testConnection(UUID id) {
        return connectionManager.testConnection(get(id));
    }

    /** 常驻连接的远程工具列表。 */
    public McpSchema.ListToolsResult remoteTools(UUID id) {
        return connectionManager.require(id).listTools();
    }

    private McpServerConfig get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "MCP 配置不存在"));
    }

    private void apply(McpServerConfig config, UpsertRequest req) {
        config.setName(req.name());
        config.setTransport(req.transport());
        config.setConnectParams(objectMapper.writeValueAsString(req.connectParams()));
        config.setEnabled(req.enabled());
    }
}

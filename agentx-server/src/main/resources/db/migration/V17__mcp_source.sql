-- MCP 配置来源化（plugin MCP 映射）：插件 .mcp.json 同步为 MCP 服务器配置,
-- source=PLUGIN 的行随插件生命周期联动;安装后默认停用——启动外部进程需用户
-- 在 MCP 管理页显式启用（信任边界）。
ALTER TABLE mcp_server_config ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'USER';
ALTER TABLE mcp_server_config ADD COLUMN plugin_id VARCHAR(160);
CREATE INDEX idx_mcp_server_config_plugin ON mcp_server_config(plugin_id);

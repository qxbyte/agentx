-- MCP client 侧：第三方 MCP server 接入配置（设计文档 §4.8）
CREATE TABLE mcp_server_config (
    id             UUID PRIMARY KEY,
    name           VARCHAR(64)  NOT NULL UNIQUE,
    transport      VARCHAR(32)  NOT NULL,          -- STDIO | STREAMABLE_HTTP
    connect_params JSONB        NOT NULL,          -- STDIO: {command,args[],env{}} / HTTP: {url,headers{}}
    enabled        BOOLEAN      NOT NULL DEFAULT FALSE,
    last_health_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

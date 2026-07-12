-- 工具注册中心运行态（定义来自代码/MCP，运行时同步；表只承载运营开关）
CREATE TABLE tool_registry (
    id            UUID PRIMARY KEY,
    name          VARCHAR(128) NOT NULL UNIQUE,
    source        VARCHAR(16)  NOT NULL,           -- CODE | MCP | HTTP
    description   VARCHAR(1024) NOT NULL DEFAULT '',
    params_schema TEXT,
    mcp_server_id UUID,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 配置化 Agent 定义（设计文档 §4.6）：业务 Agent 零代码新增
CREATE TABLE agent_definition (
    id              UUID PRIMARY KEY,
    name            VARCHAR(64)   NOT NULL UNIQUE,
    description     VARCHAR(512)  NOT NULL DEFAULT '',
    system_prompt   TEXT          NOT NULL,
    workflow_type   VARCHAR(32)   NOT NULL DEFAULT 'REACT',  -- REACT | CHAIN | ROUTING | PARALLELIZATION | ORCHESTRATOR_WORKERS | EVALUATOR_OPTIMIZER
    tool_names      JSONB,
    kb_ids          JSONB,
    model_config_id UUID,
    max_iterations  INT           NOT NULL DEFAULT 8,
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

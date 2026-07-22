-- ============================================================================
-- AgentX 全量数据库初始化脚本（schema 终态 = Flyway V1..V20 依次执行后的结果）
--
-- 用途：快速建库 / 离线审阅 / 非 Flyway 环境。正常开发与部署仍以 Flyway 迁移为准
-- （应用启动自动执行 agentx-server/src/main/resources/db/migration/）。
--
-- 注意：
--   1. 本脚本与 Flyway 二选一。若用本脚本建库后仍要接入 Flyway，
--      需先 baseline 到当前版本：spring.flyway.baseline-on-migrate=true
--      + spring.flyway.baseline-version=20，否则 Flyway 会重复建表报错。
--   2. 向量维度默认 1024（对应 AGENTX_VECTOR_DIM 默认值）；使用其他维度的
--      embedding 模型时改 vector_store.embedding 的 vector(N)。
--   3. 无种子数据：管理员账号（admin/admin123）由应用启动时 AdminSeeder 播种。
--   4. 维护约定：每新增一个 Flyway 迁移，同步更新本脚本与 docs/database-schema.md。
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- 认证与审计
-- ============================================================

CREATE TABLE sys_user (
    id            UUID PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(64),
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER',    -- ADMIN | USER
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | DISABLED
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE ai_model_config (
    id            UUID PRIMARY KEY,
    name          VARCHAR(64)  NOT NULL UNIQUE,
    provider_type VARCHAR(32)  NOT NULL,           -- DEEPSEEK | OPENAI_COMPATIBLE | OLLAMA
    base_url      VARCHAR(255),
    api_key_enc   VARCHAR(1024),                   -- AES 加密（密钥 ~/.agentx/master.key）
    model_name    VARCHAR(128) NOT NULL,
    type          VARCHAR(16)  NOT NULL,           -- CHAT | EMBEDDING | RERANK
    is_default    BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE ai_call_log (
    id                UUID PRIMARY KEY,
    user_id           UUID,
    conversation_id   UUID,
    model_name        VARCHAR(128),
    prompt_tokens     BIGINT NOT NULL DEFAULT 0,
    completion_tokens BIGINT NOT NULL DEFAULT 0,
    latency_ms        BIGINT NOT NULL DEFAULT 0,
    status            VARCHAR(16) NOT NULL,        -- OK | ERROR
    model_type        VARCHAR(16) NOT NULL DEFAULT 'CHAT',  -- CHAT | EMBEDDING | RERANK
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_call_log_user_time ON ai_call_log (user_id, created_at DESC);
CREATE INDEX idx_call_log_type_model ON ai_call_log (model_type, model_name);

-- ============================================================
-- 会话（双轨制：chat_* 业务轨真源 + SPRING_AI_CHAT_MEMORY 模型轨）
-- ============================================================

CREATE TABLE chat_conversation (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES sys_user (id),
    title           VARCHAR(255) NOT NULL DEFAULT '新对话',
    agent_id        UUID,                          -- 绑定的 Agent 定义
    kb_ids          JSONB,                         -- 会话固化的知识库集合（创建期属性）
    model_config_id UUID,                          -- 会话记住的模型选择
    workspace_id    UUID,                          -- CodeAgent：归属项目（普通对话为空）
    plan_state      JSONB,                         -- updatePlan 最近一次参数原文（任务清单恢复）
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_conversation_user ON chat_conversation (user_id, updated_at DESC);
CREATE INDEX idx_conversation_workspace ON chat_conversation (workspace_id);

CREATE TABLE chat_message (
    id                UUID PRIMARY KEY,
    conversation_id   UUID        NOT NULL REFERENCES chat_conversation (id) ON DELETE CASCADE,
    role              VARCHAR(16) NOT NULL,        -- USER | ASSISTANT | TOOL | SYSTEM
    content           TEXT        NOT NULL DEFAULT '',
    blocks            JSONB,                       -- 有序 blocks（reasoning/tool 交替时间线）：展示轨唯一真相源
    rag_sources       JSONB,                       -- RAG 命中来源
    token_usage       JSONB,                       -- {promptTokens, completionTokens}
    attachments       JSONB,                       -- 用户消息附件元数据（气泡芯片渲染）
    model_content     TEXT,                        -- 入忆版文本（skill 展开/附件占位；≠原文才写）
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_message_conversation ON chat_message (conversation_id, created_at);

-- Spring AI 官方 chat memory schema（模型轨，JdbcChatMemoryRepository 管理）
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp" TIMESTAMP NOT NULL,
    sequence_id BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX
    ON SPRING_AI_CHAT_MEMORY(conversation_id, "timestamp");
CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_SEQUENCE_ID_IDX
    ON SPRING_AI_CHAT_MEMORY(conversation_id, sequence_id);

-- 会话附件（输入框上传，解析文本注入模型；kind=image 走视觉 Media 通道）
CREATE TABLE chat_attachment (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    conversation_id UUID,
    message_id UUID,                               -- 发送时回填；空=未发送孤儿附件
    filename VARCHAR(255) NOT NULL,
    rel_path TEXT,                                 -- 文件夹上传时的相对路径
    size_bytes BIGINT NOT NULL,
    char_count INT NOT NULL,
    truncated BOOLEAN NOT NULL DEFAULT false,
    storage_path TEXT NOT NULL,
    parsed_text TEXT NOT NULL,
    kind VARCHAR(16) NOT NULL DEFAULT 'text',      -- text | image
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_attachment_message ON chat_attachment(message_id);
CREATE INDEX idx_chat_attachment_user ON chat_attachment(user_id);

-- 模型生成的文件（generateDocument/generateSpreadsheet 产物，三段式交付注册表）
CREATE TABLE generated_file (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    conversation_id UUID,
    filename VARCHAR(255) NOT NULL,
    format VARCHAR(16) NOT NULL,                   -- docx | xlsx | pptx | pdf | md
    size_bytes BIGINT NOT NULL,
    path TEXT NOT NULL,                            -- 平台存储路径
    saved_path TEXT,                               -- 编码会话 savePath 另存的工作区路径
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_generated_file_conversation ON generated_file(conversation_id);
CREATE INDEX idx_generated_file_user ON generated_file(user_id);

-- ============================================================
-- 工具 / Agent / MCP
-- ============================================================

CREATE TABLE tool_registry (
    id            UUID PRIMARY KEY,
    name          VARCHAR(128) NOT NULL UNIQUE,
    source        VARCHAR(16)  NOT NULL,           -- CODE | MCP | HTTP
    description   TEXT NOT NULL DEFAULT '',
    params_schema TEXT,
    mcp_server_id UUID,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE agent_definition (
    id              UUID PRIMARY KEY,
    name            VARCHAR(64)   NOT NULL UNIQUE,
    description     VARCHAR(512)  NOT NULL DEFAULT '',
    system_prompt   TEXT          NOT NULL,
    workflow_type   VARCHAR(32)   NOT NULL DEFAULT 'REACT',
        -- REACT | CHAIN | ROUTING | PARALLELIZATION | ORCHESTRATOR_WORKERS | EVALUATOR_OPTIMIZER
    tool_names      JSONB,
    kb_ids          JSONB,
    model_config_id UUID,
    max_iterations  INT           NOT NULL DEFAULT 8,
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    source          VARCHAR(16)   NOT NULL DEFAULT 'USER',  -- USER | PLUGIN（插件贡献，只读）
    plugin_id       VARCHAR(160),                  -- 归属插件（如 task-swarm@qxbyte-hub）
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_agent_definition_plugin ON agent_definition(plugin_id);

CREATE TABLE mcp_server_config (
    id             UUID PRIMARY KEY,
    name           VARCHAR(64)  NOT NULL UNIQUE,
    transport      VARCHAR(32)  NOT NULL,          -- STDIO | STREAMABLE_HTTP
    connect_params JSONB        NOT NULL,          -- STDIO: {command,args[],env{}} / HTTP: {url,headers{}}
    enabled        BOOLEAN      NOT NULL DEFAULT FALSE,
    last_health_at TIMESTAMPTZ,
    source         VARCHAR(16)  NOT NULL DEFAULT 'USER',   -- USER | PLUGIN
    plugin_id      VARCHAR(160),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_mcp_server_config_plugin ON mcp_server_config(plugin_id);

-- ============================================================
-- RAG 知识库
-- ============================================================

CREATE TABLE kb_knowledge_base (
    id                   UUID PRIMARY KEY,
    user_id              UUID          NOT NULL REFERENCES sys_user (id),
    name                 VARCHAR(128)  NOT NULL,
    description          VARCHAR(512)  NOT NULL DEFAULT '',
    embedding_model_id   UUID,                     -- 空则用默认 EMBEDDING 模型
    chunk_size           INT           NOT NULL DEFAULT 800,
    chunk_overlap        INT           NOT NULL DEFAULT 80,
    top_k                INT           NOT NULL DEFAULT 5,
    similarity_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_kb_user ON kb_knowledge_base (user_id);

CREATE TABLE kb_document (
    id            UUID PRIMARY KEY,
    kb_id         UUID          NOT NULL REFERENCES kb_knowledge_base (id) ON DELETE CASCADE,
    filename      VARCHAR(255)  NOT NULL,
    file_path     VARCHAR(512)  NOT NULL,
    mime_type     VARCHAR(128),
    size_bytes    BIGINT        NOT NULL DEFAULT 0,
    status        VARCHAR(16)   NOT NULL DEFAULT 'UPLOADED',  -- UPLOADED|PARSING|INGESTING|READY|FAILED
    segment_count INT           NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_doc_kb ON kb_document (kb_id, created_at DESC);

CREATE TABLE kb_segment (
    id         UUID PRIMARY KEY,
    doc_id     UUID        NOT NULL REFERENCES kb_document (id) ON DELETE CASCADE,
    kb_id      UUID        NOT NULL,
    seq_no     INT         NOT NULL,
    content    TEXT        NOT NULL,
    char_count INT         NOT NULL DEFAULT 0,
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_segment_doc ON kb_segment (doc_id, seq_no);
CREATE INDEX idx_segment_kb ON kb_segment (kb_id);

CREATE TABLE rag_ingest_task (
    id          UUID PRIMARY KEY,
    doc_id      UUID        NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'PENDING',  -- PENDING|RUNNING|SUCCEEDED|FAILED
    progress    INT         NOT NULL DEFAULT 0,          -- 0-100
    error_msg   VARCHAR(1024),
    retries     INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);
CREATE INDEX idx_ingest_doc ON rag_ingest_task (doc_id, created_at DESC);

-- Spring AI PgVectorStore（维度与 AGENTX_VECTOR_DIM 一致，默认 1024；
-- 更换不同维度 embedding 模型需重建本表并由 kb_segment 真源重灌）
CREATE TABLE vector_store (
    id        UUID PRIMARY KEY,
    content   TEXT,
    metadata  JSONB,
    embedding vector(1024)
);
CREATE INDEX idx_vector_store_hnsw ON vector_store
    USING HNSW (embedding vector_cosine_ops);
CREATE INDEX idx_vector_store_metadata ON vector_store USING GIN (metadata);

-- 外部知识库接入（固定三 API 模板：heartbeat / info / search）
CREATE TABLE external_kb (
    id             UUID PRIMARY KEY,
    name           VARCHAR(60)  NOT NULL,
    base_url       VARCHAR(300) NOT NULL,
    vault_id       VARCHAR(120) NOT NULL,           -- 外部系统内仓库标识，检索必带
    heartbeat_path VARCHAR(200) NOT NULL DEFAULT '/api/external-kb/heartbeat',
    info_path      VARCHAR(200) NOT NULL DEFAULT '/api/external-kb/info',
    search_path    VARCHAR(200) NOT NULL DEFAULT '/api/external-kb/search',
    top_k          INT          NOT NULL DEFAULT 5,
    similarity_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.2,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ============================================================
-- CodeAgent
-- ============================================================

CREATE TABLE coding_workspace (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES sys_user (id),
    name       VARCHAR(128) NOT NULL,
    root_path  VARCHAR(512) NOT NULL,               -- 服务器上的项目目录
    kb_id      UUID,                                -- 可绑定知识库检索规范
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_workspace_user ON coding_workspace (user_id);

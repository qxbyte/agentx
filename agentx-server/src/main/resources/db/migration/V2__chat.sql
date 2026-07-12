-- 会话双轨制（设计文档 §4.4）：业务轨 chat_* 表是完整历史真源；
-- 模型轨 SPRING_AI_CHAT_MEMORY 由 Spring AI JdbcChatMemoryRepository 管理（官方 schema 原样纳入 Flyway）。

CREATE TABLE chat_conversation (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES sys_user (id),
    title           VARCHAR(255) NOT NULL DEFAULT '新对话',
    agent_id        UUID,                          -- M3：绑定 Agent 定义
    kb_ids          JSONB,                         -- M4：绑定知识库集合
    model_config_id UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_conversation_user ON chat_conversation (user_id, updated_at DESC);

CREATE TABLE chat_message (
    id                UUID PRIMARY KEY,
    conversation_id   UUID        NOT NULL REFERENCES chat_conversation (id) ON DELETE CASCADE,
    role              VARCHAR(16) NOT NULL,        -- USER | ASSISTANT | TOOL | SYSTEM
    content           TEXT        NOT NULL DEFAULT '',
    reasoning_content TEXT,
    tool_calls        JSONB,
    rag_sources       JSONB,
    token_usage       JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_message_conversation ON chat_message (conversation_id, created_at);

-- Spring AI 官方 chat memory schema（schema-postgresql.sql，2.0.0，含 sequence_id）
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

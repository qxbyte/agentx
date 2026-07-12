CREATE TABLE sys_user (
    id            UUID PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(64),
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE ai_model_config (
    id            UUID PRIMARY KEY,
    name          VARCHAR(64)  NOT NULL UNIQUE,
    provider_type VARCHAR(32)  NOT NULL,           -- DEEPSEEK | OPENAI_COMPATIBLE | OLLAMA
    base_url      VARCHAR(255),
    api_key_enc   VARCHAR(1024),
    model_name    VARCHAR(128) NOT NULL,
    type          VARCHAR(16)  NOT NULL,           -- CHAT | EMBEDDING
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
    status            VARCHAR(16) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_call_log_user_time ON ai_call_log (user_id, created_at DESC);

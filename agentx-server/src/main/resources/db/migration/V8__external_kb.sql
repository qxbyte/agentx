-- 外部知识库接入（固定三 API 模板：heartbeat / info / search）。
-- vault_id：外部系统内的仓库标识（如 Obsidian vault）——检索必带，防多仓库内容互相污染。
-- enabled=false 时检索完全跳过该库（解耦开关）。
CREATE TABLE external_kb (
    id             UUID PRIMARY KEY,
    name           VARCHAR(60)  NOT NULL,
    base_url       VARCHAR(300) NOT NULL,             -- http://ip:port
    vault_id       VARCHAR(120) NOT NULL,
    heartbeat_path VARCHAR(200) NOT NULL DEFAULT '/api/external-kb/heartbeat',
    info_path      VARCHAR(200) NOT NULL DEFAULT '/api/external-kb/info',
    search_path    VARCHAR(200) NOT NULL DEFAULT '/api/external-kb/search',
    top_k          INT          NOT NULL DEFAULT 5,
    similarity_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.2,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

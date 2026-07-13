-- CodeAgent 工作区（设计文档 §3）：root_path 是服务器上待修仓库目录，kb_id 可绑定知识库以检索规范。
CREATE TABLE coding_workspace (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES sys_user (id),
    name       VARCHAR(128) NOT NULL,
    root_path  VARCHAR(512) NOT NULL,
    kb_id      UUID,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_workspace_user ON coding_workspace (user_id);

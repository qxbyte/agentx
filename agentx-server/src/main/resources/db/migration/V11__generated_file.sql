-- 模型生成的文件（generateDocument / generateSpreadsheet 产物）：
-- 三段式交付的注册表——消息只携带 fileId 引用，下载经 /api/v1/files/{id}/download 鉴权换字节流。
CREATE TABLE generated_file (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    conversation_id UUID,
    filename VARCHAR(255) NOT NULL,
    format VARCHAR(16) NOT NULL,
    size_bytes BIGINT NOT NULL,
    path TEXT NOT NULL,
    saved_path TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_generated_file_conversation ON generated_file(conversation_id);
CREATE INDEX idx_generated_file_user ON generated_file(user_id);

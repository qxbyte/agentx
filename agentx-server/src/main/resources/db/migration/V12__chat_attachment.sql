-- 会话附件：输入框上传的文件/文件夹展开文件，解析文本后随消息注入模型上下文。
-- message_id 发送时回填；未发送的孤儿附件可定期清理（M2）。
CREATE TABLE chat_attachment (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    conversation_id UUID,
    message_id UUID,
    filename VARCHAR(255) NOT NULL,
    rel_path TEXT,
    size_bytes BIGINT NOT NULL,
    char_count INT NOT NULL,
    truncated BOOLEAN NOT NULL DEFAULT false,
    storage_path TEXT NOT NULL,
    parsed_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_attachment_message ON chat_attachment(message_id);
CREATE INDEX idx_chat_attachment_user ON chat_attachment(user_id);

-- 用户消息的附件元数据（[{id, filename, sizeBytes}]），供历史气泡渲染附件芯片
ALTER TABLE chat_message ADD COLUMN attachments JSONB;

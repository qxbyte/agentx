-- CodeAgent：会话可归属一个编码项目（工作区），供侧栏按项目分组会话。
-- 可空：普通对话不绑定项目。
ALTER TABLE chat_conversation ADD COLUMN workspace_id UUID;

CREATE INDEX idx_conversation_workspace ON chat_conversation (workspace_id);

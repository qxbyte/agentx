-- 附件类型：text（解析文本注入 <documents>）| image（Spring AI Media 通道走视觉模型）
ALTER TABLE chat_attachment ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'text';

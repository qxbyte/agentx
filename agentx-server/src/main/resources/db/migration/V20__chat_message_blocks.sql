-- 消息 blocks（有序 reasoning/tool 数组）成为展示轨唯一真相源；
-- 旧的拼接版思考与平铺工具数组随之删除（存量为测试数据，不迁移）
ALTER TABLE chat_message DROP COLUMN reasoning_content;
ALTER TABLE chat_message DROP COLUMN tool_calls;
ALTER TABLE chat_message ADD COLUMN blocks JSONB;

-- 调用审计按模型类型区分：CHAT 与 EMBEDDING 分开计量（即便同厂商 chat 与 embedding 也各算各）。
-- 存量行默认 CHAT（V9 之前只审计了 CHAT 调用）。
ALTER TABLE ai_call_log ADD COLUMN model_type VARCHAR(16) NOT NULL DEFAULT 'CHAT';

-- 统计按 (model_type, model_name) 聚合，加复合索引加速后台报表。
CREATE INDEX idx_call_log_type_model ON ai_call_log (model_type, model_name);

-- 会话计划状态：updatePlan 工具最近一次调用的参数原文
-- （JSON：{"steps":[{"step":"…","status":"pending|in_progress|completed"}],"explanation":"…"}），
-- 前端刷新/切换会话时据此恢复输入框上方的计划面板。
ALTER TABLE chat_conversation ADD COLUMN plan_state JSONB;

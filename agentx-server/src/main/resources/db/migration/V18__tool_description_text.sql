-- 工具描述改为无长度限制：updatePlan 逐字移植 Claude Code TodoWrite 原文
-- （含全部示例，约 8KB），超出原 varchar(1024) 导致工具目录同步失败
ALTER TABLE tool_registry ALTER COLUMN description TYPE text;

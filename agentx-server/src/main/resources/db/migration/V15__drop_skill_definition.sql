-- skill 存储从数据库迁移到本地目录（~/.agentx/skills/，对标 Claude Code / Codex 的
-- 目录化配置：skill 属于本机而非账号）。skill_definition 表随之废弃。
DROP TABLE IF EXISTS skill_definition;

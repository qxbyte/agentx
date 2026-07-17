-- Agent 定义来源化（plugin 子 agent 适配）：插件 agents/*.md 同步为只读 Agent 定义,
-- source=PLUGIN 的行由插件安装/启停/卸载联动维护,管理端不可编辑。
ALTER TABLE agent_definition ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'USER';
ALTER TABLE agent_definition ADD COLUMN plugin_id VARCHAR(160);
CREATE INDEX idx_agent_definition_plugin ON agent_definition(plugin_id);

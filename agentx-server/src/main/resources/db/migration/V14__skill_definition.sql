-- Skill 斜杠命令定义（设计文档 2026-07-17-skill-斜杠命令设计）：
-- 用户自定义指令模板，输入框敲 /name 触发，后端展开注入模型上下文。
-- source/plugin_id 为 plugin 体系预留（M4+）；model_invocable 为模型自动触发预留（M2）。
CREATE TABLE skill_definition (
    id UUID PRIMARY KEY,
    user_id UUID,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(1024) NOT NULL,
    argument_hint VARCHAR(255),
    content TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    model_invocable BOOLEAN NOT NULL DEFAULT false,
    source VARCHAR(16) NOT NULL DEFAULT 'USER',
    plugin_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 用户名下 /name 唯一；plugin 贡献的 skill 以 plugin:name 全限定名调用，不参与此约束
CREATE UNIQUE INDEX uk_skill_definition_user_name ON skill_definition(user_id, name) WHERE source = 'USER';
CREATE INDEX idx_skill_definition_user ON skill_definition(user_id);

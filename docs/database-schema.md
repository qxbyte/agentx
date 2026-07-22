# AgentX 数据库表结构

> 本文是全项目 schema 的汇总视图（截至 Flyway **V19**）。
>
> - **权威来源**：`agentx-server/src/main/resources/db/migration/` 下的 Flyway 迁移（应用启动自动执行）。
> - **全量初始化脚本**：`deploy/init-schema.sql`——迁移终态的等价 DDL，用于快速建库/离线审阅；与 Flyway 二选一（混用需 baseline 到 V19，见脚本头注释）。
> - **维护约定**：每新增一个迁移，同步更新本文与 `deploy/init-schema.sql`。

## 总览

| 业务域 | 表 | 说明 |
|---|---|---|
| 认证与审计 | `sys_user` | 用户账号（admin 由 `AdminSeeder` 启动播种） |
| | `ai_model_config` | 模型接入配置（密钥 AES 加密） |
| | `ai_call_log` | 模型调用审计（token/延迟/状态） |
| 会话 | `chat_conversation` | 会话（含项目归属、任务清单、模型记忆） |
| | `chat_message` | 业务轨消息全量真源（永不截断） |
| | `SPRING_AI_CHAT_MEMORY` | 模型轨记忆（Spring AI 官方 schema） |
| | `chat_attachment` | 会话附件（文本解析 / 图片） |
| | `generated_file` | 模型生成文件注册表（三段式交付） |
| 工具/Agent/MCP | `tool_registry` | 工具目录（定义来自代码/MCP，表存启停） |
| | `agent_definition` | 配置化 Agent（USER/PLUGIN 双来源） |
| | `mcp_server_config` | MCP server 接入配置 |
| RAG | `kb_knowledge_base` | 知识库（分块/检索参数） |
| | `kb_document` | 文档（状态机 UPLOADED→READY） |
| | `kb_segment` | 分段真源（向量层可全量重建） |
| | `rag_ingest_task` | 入库任务（进度/重试） |
| | `vector_store` | Spring AI PgVector（HNSW 索引） |
| | `external_kb` | 外部知识库（三 API 模板接入） |
| CodeAgent | `coding_workspace` | 编码工作区（项目目录 + 可绑知识库） |

依赖：PostgreSQL 17 + **pgvector** 扩展（建库后执行 `CREATE EXTENSION IF NOT EXISTS vector;`）。

已废弃：`skill_definition`（V14 建、V15 删——skill 迁移到本地目录 `~/.agentx/skills/`）。

## 关键设计

**双轨制会话存储**（最重要的结构决策）：

- **业务轨** `chat_message`：给人看的完整历史（正文、思考过程、工具轨迹、RAG 来源、附件元数据），永不截断、永不进模型；
- **模型轨** `SPRING_AI_CHAT_MEMORY`：给模型看的上下文，由 `ModelMemoryService` 管理（附件占位、工具摘要、token 预算、滚动压缩）；
- 两轨一致性：删会话双轨同删；重新生成回滚时按 `chat_message.model_content`（入忆版文本）保真重建。

**配置类数据不入库**：skill（`~/.agentx/skills/`）、长期记忆（`~/.agentx/AGENTX.md`、`<工作区>/AGENTX.md`）、模型密钥主密钥（`~/.agentx/master.key`）都在本地文件——一机一用户的产品定位下，账号无关的本机配置走文件系统。

## 各表明细

### 认证与审计

**`sys_user`** — 用户账号

| 列 | 类型 | 说明 |
|---|---|---|
| id | UUID PK | UUIDv7 |
| username | varchar(64) UNIQUE | 登录名 |
| password_hash | varchar(100) | bcrypt |
| nickname | varchar(64) | |
| role | varchar(16) | ADMIN / USER |
| status | varchar(16) | ACTIVE / DISABLED |
| created_at | timestamptz | |

**`ai_model_config`** — 模型接入配置

| 列 | 类型 | 说明 |
|---|---|---|
| id | UUID PK | |
| name | varchar(64) UNIQUE | 展示名 |
| provider_type | varchar(32) | DEEPSEEK / OPENAI_COMPATIBLE / OLLAMA |
| base_url | varchar(255) | |
| api_key_enc | varchar(1024) | AES 加密，主密钥 `~/.agentx/master.key` |
| model_name | varchar(128) | 供应商模型 id |
| type | varchar(16) | CHAT / EMBEDDING / RERANK |
| is_default | boolean | 每 type 至多一个默认 |
| enabled | boolean | |
| created_at | timestamptz | |

**`ai_call_log`** — 调用审计。列：id、user_id、conversation_id、model_name、prompt_tokens、completion_tokens、latency_ms、status(OK/ERROR)、model_type(CHAT/EMBEDDING/RERANK，V9)、created_at。索引：`(user_id, created_at DESC)`、`(model_type, model_name)`。

### 会话

**`chat_conversation`** — 会话

| 列 | 类型 | 说明 |
|---|---|---|
| id | UUID PK | |
| user_id | UUID FK→sys_user | |
| title | varchar(255) | 默认「新对话」，小模型异步改写 |
| agent_id | UUID | 绑定 Agent 定义（可空） |
| kb_ids | jsonb | 创建期固化的知识库集合 |
| model_config_id | UUID | 会话记住的模型选择 |
| workspace_id | UUID | CodeAgent 项目归属（V7，普通对话空） |
| plan_state | jsonb | updatePlan 最近参数原文（V10，任务清单恢复） |
| created_at / updated_at | timestamptz | 列表按 updated_at 倒序 |

索引：`(user_id, updated_at DESC)`、`(workspace_id)`。

**`chat_message`** — 业务轨消息（真源）

| 列 | 类型 | 说明 |
|---|---|---|
| id | UUID PK | 助手消息 id 即 SSE meta 帧 messageId |
| conversation_id | UUID FK CASCADE | |
| role | varchar(16) | USER / ASSISTANT / TOOL / SYSTEM |
| content | text | 用户原文 / 助手正文 |
| reasoning_content | text | 思考过程 |
| tool_calls | jsonb | `[{id,name,args,result}]` |
| rag_sources | jsonb | RAG 命中来源 |
| token_usage | jsonb | `{promptTokens, completionTokens}` |
| attachments | jsonb | 附件元数据（V12，气泡芯片） |
| model_content | text | 入忆版文本（V19，skill 展开/附件占位；≠原文才写，历史接口不返回） |
| created_at | timestamptz | |

**`SPRING_AI_CHAT_MEMORY`** — 模型轨（Spring AI 官方 schema）：conversation_id、content、type、"timestamp"、sequence_id。窗口上限 200 条（兜底），实际由 token 预算 + 滚动压缩管理。

**`chat_attachment`** — 会话附件：id、user_id、conversation_id、message_id（发送回填，空=孤儿）、filename、rel_path（文件夹相对路径）、size_bytes、char_count、truncated、storage_path、parsed_text（解析全文，readAttachment 工具数据源）、kind(text/image，V13)、created_at。

**`generated_file`** — 生成文件注册表：id、user_id、conversation_id、filename、format(docx/xlsx/pptx/pdf/md)、size_bytes、path、saved_path（编码会话另存工作区）、created_at。消息只带 fileId，下载走鉴权接口。

### 工具 / Agent / MCP

**`tool_registry`** — 工具目录：id、name UNIQUE、source(CODE/MCP/HTTP)、description(text，V18 放开长度)、params_schema、mcp_server_id、enabled、created_at。定义运行时同步，表只承载运营启停。

**`agent_definition`** — 配置化 Agent：id、name UNIQUE、description、system_prompt、workflow_type(REACT/CHAIN/ROUTING/PARALLELIZATION/ORCHESTRATOR_WORKERS/EVALUATOR_OPTIMIZER)、tool_names jsonb、kb_ids jsonb、model_config_id、max_iterations、enabled、source(USER/PLUGIN，V16)、plugin_id(V16)、created_at。PLUGIN 来源随插件生命周期联动、管理端只读。

**`mcp_server_config`** — MCP 接入：id、name UNIQUE、transport(STDIO/STREAMABLE_HTTP)、connect_params jsonb、enabled（插件安装默认停用，信任边界）、last_health_at、source/plugin_id(V17)、created_at。

### RAG

**`kb_knowledge_base`**：id、user_id FK、name、description、embedding_model_id（空=默认）、chunk_size(800)、chunk_overlap(80)、top_k(5)、similarity_threshold、created_at。

**`kb_document`**：id、kb_id FK CASCADE、filename、file_path、mime_type、size_bytes、status(UPLOADED/PARSING/INGESTING/READY/FAILED)、segment_count、created_at。

**`kb_segment`**（管理真源，向量层可由此全量重建）：id、doc_id FK CASCADE、kb_id、seq_no、content、char_count、enabled、created_at。

**`rag_ingest_task`**：id、doc_id、status(PENDING/RUNNING/SUCCEEDED/FAILED)、progress(0-100)、error_msg、retries、created_at、finished_at。

**`vector_store`**（Spring AI PgVector）：id、content、metadata jsonb、embedding vector(N)。N 由 Flyway 占位符 `${vector-dim}` 注入（`AGENTX_VECTOR_DIM`，默认 1024）；HNSW 余弦索引 + metadata GIN 索引。更换维度需重建并从 `kb_segment` 重灌。

**`external_kb`**（外部知识库，固定三 API）：id、name、base_url、vault_id（多仓库隔离）、heartbeat_path、info_path、search_path、top_k、similarity_threshold、enabled、created_at。

### CodeAgent

**`coding_workspace`**：id、user_id FK、name、root_path（服务器上的项目目录）、kb_id（可绑规范知识库）、created_at。

## 表关系（ER 概述）

```
sys_user 1──n chat_conversation 1──n chat_message
                    │                     │ (attachments 元数据引用)
                    │                     └── chat_attachment (message_id 回填)
                    ├── generated_file (conversation_id)
                    ├── agent_definition (agent_id，弱引用)
                    ├── ai_model_config (model_config_id，弱引用)
                    └── coding_workspace (workspace_id，弱引用)

kb_knowledge_base 1──n kb_document 1──n kb_segment ──(重灌)→ vector_store
                                └── rag_ingest_task (doc_id)

mcp_server_config ←(mcp_server_id)─ tool_registry
```

注：除标注 FK 的以外均为**弱引用**（无外键约束）——跨模块引用不加数据库级约束，越权/悬挂由服务层校验，保持模块可独立演进。

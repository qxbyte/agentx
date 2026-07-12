# AgentX — Spring AI 企业级智能体平台设计文档

- **日期**：2026-07-12
- **状态**：已评审定稿（用户确认技术选型与架构方向）
- **定位**：基于 Spring AI 2.0.0 的企业级智能体底座。开箱提供 ChatGPT 风格对话、工具调用、Agent 编排、RAG 知识库、MCP 双侧接入五大能力；企业后续业务在此之上以「新增模块 / 新增工具 / 新增 Agent / 新增知识库」的方式迭代，不改底座。

---

## 1. 背景与目标

### 1.1 目标

1. 覆盖 Spring AI 2.0 的常用模块并按企业级生产标准落地：ChatClient、Advisors、Tool Calling、Chat Memory、RAG（ETL + Modular RAG）、VectorStore、MCP client/server、Observability。
2. ChatGPT 风格 Web UI：流式渲染、会话管理、Agent 过程可视化、RAG 引用溯源。
3. 平台可运营：知识库管理后台（上传→分段→向量化→命中测试闭环）、模型配置后台、MCP 服务管理后台。
4. 可迭代：清晰的模块边界与扩展点，业务团队照模板即可新增工具、Agent、MCP server。

### 1.2 非目标（YAGNI，明确不做）

- 微服务拆分、注册中心、配置中心（模块化单体，预留拆分边界即可）。
- 完整多租户 SaaS（只做用户级数据隔离 + RBAC，表设计预留 `tenant_id` 扩展位说明，不实现租户逻辑）。
- 可视化工作流画布（编排以代码模板呈现，画布是后续业务迭代项）。
- 计费、配额、模型评测（预留观测数据，能力后补）。

### 1.3 调研结论摘要（设计依据）

- **Spring AI 2.0.0 GA**（2026-06-12）：基线 Spring Boot 4.x / JDK 17+ / Jackson 3；工具循环重构进 `ToolCallingAdvisor`（自动注册）；MCP Java SDK 2.0（`@McpTool` 注解官方化，Streamable HTTP 为默认传输）；Modular RAG（`RetrievalAugmentationAdvisor`）成熟；`ChatMemory.CONVERSATION_ID` 必传。
- **官方 agentic 立场**：不提供重型 Agent 框架，推荐 ChatClient 组合五种 workflow 模式（chain / routing / parallelization / orchestrator-workers / evaluator-optimizer）。
- **社区共性模式**（ragent / spring-ai-alibaba / MaxKB / 芋道 / bella-openapi）：模块化单体 + 模型屏蔽层 + 会话双轨制 + RAG 分段双写 + 命中测试闭环 + SSE 事件信封。
- **官方能力边界**（需自研）：带 overlap 的分段器、异步向量化任务队列、分段管理界面、命中测试。

---

## 2. 技术选型（已确认）

| 维度 | 选型 | 理由 |
|---|---|---|
| 核心框架 | Spring AI 2.0.0 GA（BOM `org.springframework.ai:spring-ai-bom:2.0.0`） | 最新 GA，MCP/RAG/工具循环能力完整 |
| 运行时 | Spring Boot 4.x + JDK 21（开启虚拟线程） | Boot 4 是 Spring AI 2.0 强制基线；JDK 21 LTS，AI 应用 IO 密集，虚拟线程收益大 |
| 构建 | Maven 多模块 | 国内企业绝对主流 |
| 数据库 | PostgreSQL 17 | 业务表 + PGVector + 全文检索一库三用，事务一致性好，运维成本最低 |
| 向量库 | PGVector（`spring-ai-starter-vector-store-pgvector`） | <5000 万向量性能充足；经 VectorStore 抽象保留切换 Milvus 的能力 |
| 模型接入 | 默认 DeepSeek（`spring-ai-starter-model-deepseek`）；通义等走 OpenAI 兼容端点（`spring-ai-starter-model-openai` 改 base-url）；Ollama（`spring-ai-starter-model-ollama`）私有化 | 国内网络直连、成本低；多供应商屏蔽层支持运行时切换 |
| Embedding | 默认通义 text-embedding-v4（OpenAI 兼容），可切 Ollama bge-m3 | 中文效果好，私有化有替代 |
| 前端 | React 18 + TypeScript + Vite + antd | 用户选择；AI 组件生态丰富 |
| 鉴权 | Spring Security + JWT + RBAC | 底座合理起点，可扩 SSO |
| 可观测 | Micrometer（Spring AI 内建）→ OTLP | token 用量、调用耗时开箱即得 |
| 部署 | Docker Compose（`pgvector/pgvector:pg17` + 后端 + 前端 nginx） | 单体单机起步，K8s 后补 |

---

## 3. 总体架构

```
┌────────────────────────  agentx-web (React + TS)  ────────────────────────┐
│  对话区（ChatGPT 风格） │ 知识库管理 │ 管理后台（模型/MCP/工具/用户）        │
└──────────────────────────────┬─────────────────────────────────────────────┘
                        REST + SSE (JWT)
┌──────────────────────────────┴───────────────────────  agentx-server  ─────┐
│                       REST API 层（Controller / 鉴权 / 参数校验）            │
├─────────────┬──────────────┬─────────────┬─────────────┬───────────────────┤
│ agentx-chat │ agentx-agent │ agentx-rag  │ agentx-mcp  │ agentx-tools      │
│ 会话/流式    │ 编排/loop    │ 知识库/检索  │ MCP client  │ 工具注册中心       │
├─────────────┴──────────────┴─────────────┴─────────────┴───────────────────┤
│           agentx-infra-ai（模型屏蔽层：ChatClientFactory / SSE 信封）        │
├─────────────────────────────────────────────────────────────────────────────┤
│              Spring AI 2.0（ChatClient / Advisors / VectorStore / MCP）      │
├──────────────┬───────────────────────┬──────────────────────────────────────┤
│ PostgreSQL17 │  PGVector (同库)       │  外部：DeepSeek / 通义 / Ollama / MCP │
└──────────────┴───────────────────────┴──────────────────────────────────────┘

独立部署单元：agentx-mcp-server（MCP server 模板，stdio / streamable-http 双模式）
```

**分层铁律**：业务模块（chat/agent/rag/mcp/tools）不直接触碰任何 provider SDK 或 `*ChatModel`，一律经 `agentx-infra-ai` 拿 `ChatClient`。这是「换模型不改业务代码」的保证。

---

## 4. Maven 模块设计

```
agentx/  (parent pom：BOM 管理、JDK 21、插件统一)
├── agentx-common          依赖：无业务依赖
├── agentx-infra-ai        依赖：common
├── agentx-auth            依赖：common
├── agentx-tools           依赖：common, infra-ai
├── agentx-chat            依赖：common, infra-ai, auth
├── agentx-rag             依赖：common, infra-ai, auth
├── agentx-agent           依赖：common, infra-ai, tools, rag(检索能力)
├── agentx-mcp             依赖：common, infra-ai, tools(注入远程工具)
├── agentx-server          依赖：以上全部（唯一 Spring Boot 应用）
├── agentx-mcp-server      独立 Spring Boot 应用（仅依赖 common）
└── agentx-web             React 前端（独立构建，产物由 nginx 服务）
```

### 4.1 agentx-common

统一响应 `ApiResponse<T>`、业务异常体系（`BizException` + 全局 `@RestControllerAdvice`）、错误码枚举、分页对象、ID 生成（时间序 UUID v7）。

### 4.2 agentx-infra-ai（模型屏蔽层，全平台核心）

| 类 | 职责 |
|---|---|
| `ModelProviderConfig`（实体） | 对应 `ai_model_config` 表：provider 类型、base-url、api-key（加密存储）、模型名、启用状态、默认标记 |
| `ChatClientFactory` | 按模型配置构建并缓存 `ChatClient`（Caffeine，配置变更事件驱逐）；provider 类型 → `ChatModel` 构建策略（DEEPSEEK / OPENAI_COMPATIBLE / OLLAMA 三种策略枚举） |
| `EmbeddingModelFactory` | 同上，构建 `EmbeddingModel`；知识库绑定 embedding 模型（换 embedding 必须重建向量，工厂层校验） |
| `SseEnvelope` / `SseEmitterSender` | SSE 事件信封（见 §7）；线程安全发送封装（complete/timeout/error 状态机，防 broken pipe 异常扩散） |
| `AiAuditAdvisor` | 自定义 Advisor：请求/响应审计落库（脱敏），token 用量记入 `ai_call_log` |

**Provider 策略说明**：DeepSeek 用官方 starter；通义/智谱/Moonshot 等一律走 OpenAI 兼容策略（改 base-url + key），不引入社区维护的专用 starter，避免版本兼容矩阵失控（spring-ai-alibaba 混用 starter 的 `NoSuchMethodError` 前车之鉴）。

### 4.3 agentx-auth

`sys_user` / `sys_role` 表，BCrypt 密码，登录颁发 JWT（access 2h + refresh 7d），`SecurityFilterChain` 全局鉴权，`@CurrentUser` 参数解析器。角色仅 `ADMIN` / `USER` 两级：ADMIN 可见管理后台，USER 只有对话与自己的知识库。

### 4.4 agentx-chat（会话双轨制）

- **业务轨**：`chat_conversation` / `chat_message` 表——完整历史、标题（首轮后异步用小模型生成）、消息重新生成、引用溯源 JSON。
- **模型轨**：Spring AI `JdbcChatMemoryRepository`（starter 自带 schema，含 2.0 的 `sequence_id` 列）+ `MessageWindowChatMemory`（窗口 20 条）。`conversationId` 即业务会话 UUID，经 `MessageChatMemoryAdvisor` 传入。
- **两轨永不互查**：业务展示读业务表；模型上下文由 ChatMemory 自治。删除会话时两轨同事务清理。
- `ChatService.streamChat(userId, conversationId, content, options)`：组装 advisor 链（memory → rag(可选) → toolcalling(自动)）→ `chatClient.prompt().stream()` → 转 SSE 信封推送；`bufferTimeout(24, 50ms)` 合帧防 token 过碎。

### 4.5 agentx-tools（工具三级注册中心）

| 级别 | 来源 | 机制 |
|---|---|---|
| L1 代码级 | 业务模块里带 `@Tool` 方法的 `@Component` | 启动扫描注册；企业迭代业务工具的主通道 |
| L2 MCP 远程 | agentx-mcp 连接的第三方 MCP server | 连接成功后其 tools 动态注入注册中心 |
| L3 HTTP 动态 | （预留接口，不实现）管理后台配置 OpenAPI 端点转工具 | 定义 `HttpToolDefinition` SPI，留空实现 |

- `ToolRegistry`：统一目录（名称/描述/来源/参数 schema/启用状态），供管理后台展示与 Agent 按需选取。
- **ToolContext 约定**：调用时统一注入 `Map.of("userId", …, "conversationId", …)`，工具实现经 `ToolContext` 取，禁止工具内部访问 SecurityContext（Agent 异步执行时无请求线程）。
- 内置示例工具（即模板）：`DateTimeTools`（returnDirect 示例）、`WeatherTools`（外部 HTTP 示例）、`KnowledgeSearchTools`（跨模块调 RAG 检索示例）、`SqlQueryTools`（只读白名单表查询示例，展示企业数据接入范式）。

### 4.6 agentx-agent（编排层）

- **ReAct loop**：直接用 2.0 自动注册的 `ToolCallingAdvisor`（`maxIterations` 限制循环上限），每轮工具调用经自定义 `AgentStepListener` 转 SSE `tool-call` / `tool-result` 事件，前端折叠展示推理过程。
- **五种 workflow 模式**：按官方 agentic-patterns 参考实现落成轻量编排器（纯 Java 组合 ChatClient，无框架依赖）：
  - `ChainWorkflow`（顺序加工）
  - `RoutingWorkflow`（分类路由到专家 prompt）
  - `ParallelizationWorkflow`（分片并行 + 聚合，虚拟线程池）
  - `OrchestratorWorkersWorkflow`（编排者拆任务→工人并行→汇总）
  - `EvaluatorOptimizerWorkflow`（生成→评估→迭代，带最大轮数）
- **Agent 定义**：`agent_definition` 表（名称、系统 prompt、绑定工具列表、绑定知识库、模型配置、workflow 类型、maxIterations）。管理后台 CRUD，对话区可选 Agent 发起会话——**企业迭代业务 Agent 不写代码，配置即得**；复杂编排才写 Workflow 子类。
- 每种 workflow 各带一个可运行示例 Agent（种子数据）。

### 4.7 agentx-rag（知识库）

**管理闭环（五件套）**：

1. **上传解析**：`TikaDocumentReader`（pdf/docx/md/html/txt…），原文件存本地磁盘目录（路径可配，预留 S3 SPI）。
2. **可配分段**：自研 `OverlappingTokenSplitter`（chunkSize/overlap/分隔符优先级，弥补官方 `TokenTextSplitter` 无 overlap 短板）；库级分段配置。
3. **异步向量化**：`rag_ingest_task` 任务表（PENDING/RUNNING/SUCCEEDED/FAILED + 进度百分比 + 错误信息），虚拟线程执行器消费，失败可重试；文档级状态机。
4. **分段双写**：`kb_segment` 表（内容、序号、启用开关、字符数）为管理真源；PGVector `vector_store` 表存 embedding，metadata 带 `kbId/docId/segmentId`。编辑分段 → 重算该段向量（同事务标记 + 异步刷新）；禁用分段 → 删向量保留行。
5. **命中测试**：管理页输入问题 → `VectorStoreDocumentRetriever`（topK/similarityThreshold 可调，filterExpression 按 kbId）→ 展示命中分段与分数 → 就地编辑。

**检索侧（对话消费）**：`RetrievalAugmentationAdvisor` 组装——`RewriteQueryTransformer`（多轮改写）+ `VectorStoreDocumentRetriever`（kbId filter）+ `ContextualQueryAugmenter`（`allowEmptyContext(true)`，未命中时不硬拒答）。命中文档经 advisor context（`retrievedDocuments`）转 SSE `rag-source` 事件，前端渲染引用角标。

### 4.8 agentx-mcp（client 侧）+ agentx-mcp-server（server 模板）

**client 侧**：
- `mcp_server_config` 表：名称、传输类型（STDIO / STREAMABLE_HTTP）、连接参数（command+args 或 url+headers）、启用状态。
- `McpConnectionManager`：按配置动态建 `McpSyncClient`（不走 starter 静态配置，因为要运行时增删）；连接成功 → `listTools()` → 包装为 `ToolCallback` 注入 `ToolRegistry`（L2）；健康检查 + 断线重连；管理页可测试连接、查看远程工具列表。

**server 模板（独立应用）**：
- `spring-ai-starter-mcp-server-webmvc`，`spring.ai.mcp.server.protocol=STREAMABLE`（可切 stdio profile）。
- 用官方注解示例三件套：`@McpTool`（订单查询模拟）、`@McpResource`（文档资源）、`@McpPrompt`（prompt 模板）。
- 附 README：企业照此模板把存量系统包成 MCP server，再到 agentx 管理页一配即接入。

### 4.9 agentx-server

唯一 Boot 启动模块：聚合装配、全部 REST Controller、SpringDoc OpenAPI、CORS、静态资源兜底。`spring.threads.virtual.enabled=true`。

---

## 5. 数据模型（核心 DDL 摘要）

```sql
-- 鉴权
sys_user(id, username uniq, password_hash, nickname, role, status, created_at)

-- 模型配置（infra-ai）
ai_model_config(id, name, provider_type, base_url, api_key_enc, model_name,
                type,          -- CHAT | EMBEDDING
                is_default, enabled, created_at)
ai_call_log(id, user_id, conversation_id, model_name, prompt_tokens,
            completion_tokens, latency_ms, status, created_at)

-- 会话双轨（业务轨）
chat_conversation(id, user_id, title, agent_id null, kb_ids jsonb, model_config_id,
                  created_at, updated_at)
chat_message(id, conversation_id, role,  -- USER|ASSISTANT|TOOL
             content, reasoning_content, tool_calls jsonb, rag_sources jsonb,
             token_usage jsonb, created_at)
-- 模型轨：Spring AI JdbcChatMemoryRepository 自带 schema（含 sequence_id）

-- 知识库
kb_knowledge_base(id, user_id, name, description, embedding_model_id,
                  chunk_size, chunk_overlap, top_k, similarity_threshold, created_at)
kb_document(id, kb_id, filename, file_path, mime_type, size_bytes,
            status,        -- UPLOADED|PARSING|INGESTING|READY|FAILED
            segment_count, created_at)
kb_segment(id, doc_id, kb_id, seq_no, content, char_count, enabled, created_at)
rag_ingest_task(id, doc_id, status, progress, error_msg, retries, created_at, finished_at)
-- 向量：PGVector 默认 vector_store 表（metadata: kb_id, doc_id, segment_id）

-- Agent 与工具
agent_definition(id, name, description, system_prompt, workflow_type, -- REACT|CHAIN|...
                 tool_names jsonb, kb_ids jsonb, model_config_id,
                 max_iterations, enabled, created_at)
tool_registry(id, name uniq, source,  -- CODE|MCP|HTTP
              description, params_schema jsonb, mcp_server_id null, enabled)

-- MCP
mcp_server_config(id, name, transport, -- STDIO|STREAMABLE_HTTP
                  connect_params jsonb, enabled, last_health_at, created_at)
```

所有表带 `id`（UUID v7）与审计时间列；Flyway 管 migration；预留说明：多租户时全表加 `tenant_id` + 复合索引。

---

## 6. REST API 契约（一级清单）

| 分组 | 端点（前缀 /api/v1） | 说明 |
|---|---|---|
| auth | POST /auth/login, /auth/refresh, GET /auth/me | JWT |
| chat | POST /chat/stream（SSE）；conversations CRUD；GET messages；POST /messages/{id}/regenerate | 对话主链路 |
| agent | agents CRUD（ADMIN）；GET /agents（可用列表） | 配置化 Agent |
| kb | knowledge-bases CRUD；POST /{id}/documents（上传）；documents/segments CRUD；POST /{id}/hit-test | 知识库闭环 |
| tools | GET /tools；PATCH /tools/{name}/enabled（ADMIN） | 工具目录 |
| mcp | mcp-servers CRUD；POST /{id}/test-connection；GET /{id}/tools（ADMIN） | MCP 管理 |
| admin | model-configs CRUD；users CRUD；GET /stats/tokens | 后台 |

统一响应 `{code, message, data}`；SSE 端点除外（原生 event-stream）。

---

## 7. SSE 事件信封协议（前后端契约）

`POST /api/v1/chat/stream`（`@microsoft/fetch-event-source` 消费，支持 POST + JWT header）。每帧 `data:` 为 JSON：

```jsonc
{ "type": "meta",        "conversationId": "…", "messageId": "…" }   // 首帧
{ "type": "text-delta",  "delta": "…" }                              // 正文增量
{ "type": "reasoning",   "delta": "…" }                              // 思考增量（deepseek-reasoner 等）
{ "type": "tool-call",   "id": "…", "name": "…", "args": {…} }       // Agent 发起工具调用
{ "type": "tool-result", "id": "…", "name": "…", "result": "…" }     // 工具返回（截断展示）
{ "type": "rag-source",  "sources": [{docId, docName, segmentId, score, snippet}] }
{ "type": "done",        "usage": {promptTokens, completionTokens}, "finishReason": "…" }
{ "type": "error",       "code": "…", "message": "…" }               // 业务错误也走帧，不断流
```

运维要求写入部署文档：nginx `proxy_buffering off` + `X-Accel-Buffering: no` + `gzip off` + `proxy_read_timeout 300s`；服务端 15s 心跳注释帧。

---

## 8. 关键流程

### 8.1 普通对话（含 RAG）

登录 → 建会话（可选绑定 Agent / 知识库 / 模型）→ POST /chat/stream → ChatService：存用户消息 → ChatClientFactory 取 client → advisor 链 = MessageChatMemoryAdvisor + [RetrievalAugmentationAdvisor 若绑库] + AiAuditAdvisor（ToolCallingAdvisor 自动注册）→ stream() → 信封转发 → done 帧后落 assistant 消息（全文 + sources + usage，同帧内容一致性由服务端聚合保证）。

### 8.2 Agent 会话（ReAct）

同上，system prompt / 工具集 / maxIterations 取自 `agent_definition`；`ToolRegistry` 按 `tool_names` 解析出 `ToolCallback` 集合（含 MCP 远程工具）传入 `.toolCallbacks(...)`；工具循环事件经监听器转 tool-call/tool-result 帧。

### 8.3 知识库摄取

上传 → 存文件 + `kb_document(UPLOADED)` → 建 `rag_ingest_task(PENDING)` → 虚拟线程消费：Tika 解析 → OverlappingTokenSplitter 分段 → 批量写 `kb_segment` → `EmbeddingModelFactory` 取模型 → `TokenCountBatchingStrategy` 分批 embed → 写 PGVector → 文档置 READY / 失败置 FAILED（可重试）。进度按分段批次更新，前端轮询任务状态。

### 8.4 第三方 MCP 接入

管理页新增配置 → test-connection（initialize + listTools 预览）→ 启用 → `McpConnectionManager` 建连 → 工具注入 ToolRegistry（L2）→ Agent 配置里勾选 → 对话中即可被模型调用。断连时注册中心内对应工具标记不可用，Agent 侧跳过。

---

## 9. 错误处理与安全

- **错误分层**：业务异常（BizException→统一响应/SSE error 帧）；模型调用异常（重试 1 次→降级提示帧，审计落库）；工具执行异常（包装为工具错误结果返给模型，让模型自行解释，不断流）；向量化异常（任务 FAILED + 可重试，不影响已就绪文档）。
- **安全**：api-key AES-GCM 加密落库（主密钥走环境变量）；prompt 注入基线防护（SafeGuardAdvisor 敏感词 + system prompt 隔离用户输入）；SQL 示例工具白名单表 + 只读账号；上传文件类型/大小白名单；SSE 端点同样走 JWT；审计日志脱敏。
- **资源保护**：maxIterations 封顶工具循环；每用户并发流上限（信号量）；上传/摄取任务队列长度上限。

## 10. 可观测性

Spring AI 内建 Micrometer 指标（`gen_ai_client_operation_seconds`、`gen_ai_client_token_usage_total`、`db_vector_client_operation_seconds`、`execute_tool <name>` span）→ OTLP exporter（可对接 Langfuse/Grafana，Compose 提供注释掉的可选服务）；`ai_call_log` 表提供业务级 token 统计（管理后台报表）。敏感内容日志默认关闭。

## 11. 部署

`docker-compose.yml`：`pgvector/pgvector:pg17`（初始化脚本启用 vector 扩展）+ `agentx-server`（多阶段 Dockerfile，JRE 21）+ `agentx-web`（nginx，带 SSE 配置模板）+ 可选 `ollama`。Flyway 启动自动建表；`.env.example` 给全量环境变量清单（DB、JWT secret、加密主密钥、默认模型 key）。

## 12. 测试策略

- **单测**：编排器（五种 workflow，mock ChatClient）、OverlappingTokenSplitter、SSE 信封、ToolRegistry、加密工具。
- **集成测试**：Testcontainers（pgvector 镜像）跑 RAG 摄取全链路、会话双轨读写、Flyway migration；模型调用以 WireMock 模拟 OpenAI 兼容协议（含流式 SSE 响应），CI 不依赖真实 key。
- **冒烟**：Compose 起全栈后的脚本化 happy-path（登录→建库→上传→命中测试→对话）。

## 13. 企业迭代指南（底座扩展点）

| 想加什么 | 怎么加 | 改哪里 |
|---|---|---|
| 业务工具 | 写 `@Tool` Bean 放业务模块 | 新增类，自动进注册中心 |
| 业务 Agent | 管理后台配置（prompt+工具+知识库） | 零代码 |
| 复杂编排 | 继承 Workflow 模板类 | agentx-agent 内新增 |
| 内部系统接入 | 照 agentx-mcp-server 模板包一层 | 新仓库/新模块 + 管理页配置 |
| 新模型供应商 | OpenAI 兼容的只加一行配置；私有协议实现 Provider 策略 | infra-ai 一个类 |
| 业务领域模块 | 新建 agentx-xxx 模块，依赖 infra-ai/tools | parent pom 加 module |

---

## 附录 A：里程碑划分（供实现计划参考）

1. **M1 骨架**：parent pom + common + infra-ai + auth + server 起步，Compose + Flyway + 登录可用。
2. **M2 对话**：chat 模块 + SSE 信封 + 前端对话区（流式渲染），双轨落库。
3. **M3 工具与 Agent**：tools 注册中心 + 示例工具 + ReAct loop + 五种 workflow + Agent 配置化 + 前端过程可视化。
4. **M4 RAG**：知识库五件套 + 检索 advisor + 引用溯源 + 前端知识库管理页。
5. **M5 MCP**：client 管理 + mcp-server 模板 + 前端 MCP 管理页。
6. **M6 收尾**：管理后台（模型/用户/统计）+ 可观测接线 + 测试补全 + 部署文档。

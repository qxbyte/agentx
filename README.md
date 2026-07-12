# AgentX

**基于 Spring AI 2.0 的企业级智能体平台底座。**

开箱即用的 ChatGPT 风格对话、工具调用、Agent 编排、RAG 知识库与 MCP 双侧接入；企业业务以「新增工具 / 配置 Agent / 建知识库 / 包 MCP server / 加业务模块」的方式在其上持续迭代，不改底座。

## 能力矩阵

| 能力 | 实现 | 说明 |
|---|---|---|
| 流式对话 | SSE 事件信封（8 种帧类型） | ChatGPT 风格 UI，思考过程/工具调用/引用溯源全程可视化 |
| 多模型接入 | ChatModelProvider 策略 SPI | DeepSeek 官方 · 通义/智谱/vLLM 等 OpenAI 兼容端点 · Ollama 私有化；运行时配置切换，api-key AES-GCM 加密 |
| 会话记忆 | 双轨制 | 业务表存完整历史；Spring AI `JdbcChatMemoryRepository` 管模型上下文窗口 |
| 工具调用 | 三级注册中心 | L1 代码级（`@AgentTool`+`@Tool`）· L2 MCP 远程 · L3 HTTP（预留 SPI）；启停运营开关、循环上限守卫、异常降级 |
| Agent | 配置化 + 五种官方 workflow | `agent_definition` 零代码配置 ReAct Agent；Chain/Routing/Parallelization/Orchestrator-Workers/Evaluator-Optimizer 编排器（虚拟线程并行） |
| RAG 知识库 | 五件套运营闭环 | Tika 解析 → 自研 overlap 分段 → 异步向量化（任务/进度/重试）→ 分段双写（可编辑真源）→ 命中测试（测-看-改） |
| MCP | 双侧 | client：配置表驱动动态接入第三方（STDIO / Streamable-HTTP）；server：`@McpTool` 模板应用，企业包装存量系统的范本 |
| 鉴权 | JWT + RBAC | ADMIN / USER 两级，数据按用户隔离（越权 404） |
| 可观测 | Micrometer（Spring AI 内建） | token 用量 / 调用延迟 / 向量检索 / 工具执行 span；OTLP 导出配置模板；业务级统计报表 API |

## 架构

```
┌────────────────────────  agentx-web (React 18 + TS + antd)  ────────────────────┐
│   对话区（SSE 流式/思考块/工具卡片/引用角标） │ 知识库管理 │ 管理后台             │
└───────────────────────────────┬──────────────────────────────────────────────────┘
                          REST + SSE (JWT)
┌───────────────────────────────┴────────────────────  agentx-server（唯一启动器）─┐
├──────────────┬──────────────┬──────────────┬──────────────┬─────────────────────┤
│ agentx-chat  │ agentx-agent │ agentx-rag   │ agentx-mcp   │ agentx-tools        │
│ 会话双轨/流式 │ 编排/ReAct   │ 知识库/检索   │ MCP client   │ 工具注册中心         │
├──────────────┴──────────────┴──────────────┴──────────────┴─────────────────────┤
│      agentx-infra-ai —— 模型屏蔽层（唯一模型出口）                                │
│      ChatClientFactory / EmbeddingModelFactory（Provider SPI + 缓存 + 事件驱逐）  │
│      SseEvent 信封 / ChatStreamCustomizer SPI / AES-GCM / 调用审计                │
├──────────────────────────────────────────────────────────────────────────────────┤
│      Spring AI 2.0（ChatClient/Advisors/ToolCalling/Modular RAG/ChatMemory/MCP）  │
├───────────────┬──────────────────────┬───────────────────────────────────────────┤
│ PostgreSQL 17 │ PGVector（同库）      │ DeepSeek / 通义 / Ollama / 第三方 MCP      │
└───────────────┴──────────────────────┴───────────────────────────────────────────┘

独立部署：agentx-mcp-server（MCP server 模板，Streamable-HTTP :8090 / stdio）
```

**分层铁律**：业务模块不触碰任何 provider SDK，一律经 `agentx-infra-ai` 的工厂获取模型客户端——换模型供应商不改业务代码。模块间扩展点全部走 SPI（`ChatModelProvider` / `ToolSource` / `ChatStreamCustomizer` / `Workflow`），新增实现零改动既有代码。

## 技术栈

| 维度 | 选型 |
|---|---|
| 核心 | Spring AI **2.0.0 GA** · Spring Boot **4.1** · JDK **21**（虚拟线程） |
| 数据 | PostgreSQL 17 + PGVector（业务 + 向量一库两用）· Flyway · Spring Data JPA |
| 前端 | React 18 · TypeScript(strict) · Vite · antd v5 · zustand |
| 构建 | Maven 多模块（10 模块） |

## 快速开始

### 本地开发

```bash
# 依赖：JDK 21、Maven、PostgreSQL 17 + pgvector（brew install maven postgresql@17 pgvector）
brew services start postgresql@17
psql -d postgres -c "CREATE ROLE agentx LOGIN PASSWORD 'agentx' CREATEDB;"
psql -d postgres -c "CREATE DATABASE agentx OWNER agentx;"
psql -d agentx   -c "CREATE EXTENSION IF NOT EXISTS vector;"

# 后端（:8080，Flyway 自动建表，种子 admin/admin123）
mvn -pl agentx-server -am install -DskipTests
mvn -pl agentx-server spring-boot:run

# 前端（:5173，代理 /api → 8080）
cd agentx-web && npm install && npm run dev

# MCP server 模板（可选，:8090）
mvn -pl agentx-mcp-server spring-boot:run
```

登录 `admin / admin123` → 管理后台先配一个 **CHAT 模型**（如 DeepSeek：填 api-key、modelName=`deepseek-chat`，标记默认）与一个 **EMBEDDING 模型**（如通义 OpenAI 兼容端点 `text-embedding-v4`），即可对话与建知识库。

### Docker 部署

```bash
cp .env.example .env    # 必填 AGENTX_JWT_SECRET 与 AGENTX_MASTER_KEY（openssl rand -base64 32）
docker compose up -d --build
# web :80 · server :8080 · mcp-server 模板 :8090 · postgres(pgvector)
```

### 测试

```bash
mvn verify                      # 40+ 后端测试（需本地 PG 的 agentx_test 库）
cd agentx-web && npm run build  # 前端构建 + tsc 严格检查
```

## 目录与模块

| 模块 | 职责 | 关键类 |
|---|---|---|
| `agentx-common` | 统一响应/异常/UUIDv7 | `ApiResponse` `BizException` `UuidV7` |
| `agentx-infra-ai` | 模型屏蔽层 | `ChatClientFactory` `EmbeddingModelFactory` `ChatModelProvider(SPI)` `SseEvent` `ChatStreamCustomizer(SPI)` `ApiKeyCrypto` `AiCallAuditor` |
| `agentx-auth` | JWT/RBAC/用户管理 | `JwtService` `SecurityConfig` `@CurrentUser` |
| `agentx-tools` | 工具注册中心 | `ToolRegistry` `ToolSource(SPI)` `@AgentTool` + 内置示例（时间/天气/只读 SQL） |
| `agentx-chat` | 会话双轨 + 流式主链路 | `ChatStreamService` `ConversationService` `ChatMemoryConfig` |
| `agentx-agent` | Agent 编排 | `AgentStreamCustomizer` `SseNotifyingToolCallback`（循环守卫+过程可视化）`WorkflowRunner` + 五种 `Workflow` |
| `agentx-rag` | 知识库 | `RagIngestService` `OverlappingTextSplitter` `HitTestService` `RagStreamCustomizer` `VectorStoreFactory` |
| `agentx-mcp` | MCP client | `McpConnectionManager` `McpToolSource(L2)` |
| `agentx-mcp-server` | MCP server 模板 | `@McpTool/@McpResource/@McpPrompt` 三件套（见其 README） |
| `agentx-server` | 唯一启动器 | Flyway 迁移（V1–V5）、全局装配 |
| `agentx-web` | 前端 | 见 `agentx-web/README.md` |

## SSE 流式协议（前后端契约）

`POST /api/v1/chat/stream`，每帧 `data:` 为单层 JSON：

```jsonc
{"type":"meta","conversationId":"…","messageId":"…"}      // 首帧
{"type":"text-delta","delta":"…"}                          // 正文增量
{"type":"reasoning","delta":"…"}                           // 思考增量（deepseek-reasoner 等）
{"type":"tool-call","id":"…","name":"…","args":"…"}        // Agent 发起工具调用
{"type":"tool-result","id":"…","name":"…","result":"…"}    // 工具返回
{"type":"rag-source","sources":[{docId,docName,segmentId,score,snippet}]}
{"type":"done","usage":{promptTokens,completionTokens},"finishReason":"stop"}
{"type":"error","code":"…","message":"…"}                  // 业务错误走帧不断流
```

反代注意：SSE 端点必须关缓冲/gzip、拉长超时——见 `agentx-web/nginx.conf` 与 `deploy/nginx-sse.conf.example`。

## 企业迭代指南

| 想加什么 | 怎么加 | 改动范围 |
|---|---|---|
| 业务工具 | 写个类打 `@AgentTool`，方法打 `@Tool` | 新增一个类，自动进注册中心 |
| 业务 Agent | 管理后台配置（prompt + 工具 + 知识库 + workflow 类型） | 零代码 |
| 复杂编排 | 实现 `Workflow` 接口注册为 bean | agentx-agent 新增一个类 |
| 内部系统接入 | 复制 `agentx-mcp-server` 模板包一层，管理后台一配即入 | 新模块/新仓库 |
| 新模型供应商 | OpenAI 兼容的只加一条配置；私有协议实现 `ChatModelProvider` | infra-ai 一个类 |
| 业务领域模块 | 新建 `agentx-xxx`，依赖 infra-ai/tools | parent pom 加一行 |
| 接 SSO / 多租户 | 替换 auth 模块登录入口；全表已预留扩展位说明 | auth 模块内 |

## 安全与生产清单

- `AGENTX_JWT_SECRET`、`AGENTX_MASTER_KEY`（api-key 加密主密钥）生产必须显式设置；主密钥缺省时用随机密钥并告警（重启后已存密文不可解，仅限开发）。
- 首启后立即修改 admin 密码（或经 `AGENTX_ADMIN_PASSWORD` 预设强密码）。
- 内置 `SqlQueryTools` 演示三道防线（白名单/语句形态/行数上限），生产建议叠加只读库账号。
- Prompt 注入防护基线：system prompt 与用户输入隔离；如需敏感词拦截，在 `ChatStreamService` 的 advisor 链加 `SafeGuardAdvisor`（Spring AI 内建）。
- MCP server 端点不要裸暴露公网，网关层加鉴权。

## 可观测

Spring AI 内建 Micrometer 指标开箱即得：`gen_ai_client_operation_seconds`、`gen_ai_client_token_usage_total`、`db_vector_client_operation_seconds`、`execute_tool <name>` span。对接 OTLP（Grafana / Langfuse）取消 `application.yml` 中注释即可。业务级报表走 `/api/v1/admin/stats/*`（总量/按日/按模型）。

## 设计文档

完整设计文档与各里程碑实现计划维护在项目外部知识库（`2026-07-12-agentx-design.md` 及 M1–M6 计划），含技术选型论证、调研结论、数据模型与关键流程。

## License

Apache-2.0

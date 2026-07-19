# AgentX

**基于 Spring AI 2.0 的个人本地智能体工作台（本机工具执行 + Web UI）。**

AgentX 是一个**运行在本机的 AI 工作台**：浏览器只是 UI 外壳，后端与你同机，因此模型可以真实地读写本地文件、执行 shell 命令、运行技能脚本——同时拥有流式对话、RAG 知识库、Agent 编排与 MCP 双侧接入。技能（Skill）与插件（Plugin）体系兼容 Claude Code 插件格式，`obra/superpowers` 等现成插件市场可直接安装使用。

## 能力矩阵

| 能力 | 实现 | 说明 |
|---|---|---|
| 流式对话 | SSE 事件信封（12 种帧类型） | 思考过程/工具调用/审批/提问/引用溯源全程可视化；空会话开场输入框居中，浅色/深色/跟随系统主题 |
| 多模型接入 | ChatModelProvider 策略 SPI | DeepSeek 官方 · 通义/智谱/vLLM 等 OpenAI 兼容端点 · Ollama 私有化；运行时配置切换、按会话记忆模型选择，api-key AES-GCM 加密 |
| 会话记忆 | 双轨制 | 业务表存完整历史；Spring AI `JdbcChatMemoryRepository` 管模型上下文窗口 |
| 工具调用 | 三级注册中心 | L1 代码级（`@AgentTool`+`@Tool`）· L2 MCP 远程 · L3 HTTP（预留 SPI）；启停运营开关、循环上限守卫、异常降级 |
| 本地天然工具 | 普通对话即有（本地 app 定位） | 文件读写/目录列举/grep/find/shell/webFetch/webSearch；家目录沙箱 + 写操作审批，Plan/Ask/Auto/Bypass 四档模式对普通对话同样生效 |
| Skill 技能 | 本地目录化，格式兼容 Claude Code | `~/.agentx/skills/` 本地目录化；`/` 斜杠命令自动展开 + 模型按描述自动触发（M2）；SKILL.md frontmatter 全量解析（`user-invocable` / `disable-model-invocation`）；L1 元数据 → L2 正文 → L3 资源（references/scripts）渐进披露，scripts 经审批本地真实执行 |
| Plugin 插件 | 兼容 Claude Code 插件/市场格式 | marketplace 添加（github/url/本地路径）→ 安装/更新/启停/卸载；插件贡献技能（`plugin:skill` 命名空间）、子代理（只读同步 + `dispatchAgent` 派遣）、MCP server（默认停用的信任边界） |
| 人机交互 | askUserQuestion + 审批门 + 任务清单 | 提问卡（单选/多选/预览选择器/其他自由输入/链式多问）；危险操作审批卡；两者**无限期等待**（虚拟线程挂起零成本）；`updatePlan` 任务清单（content/activeForm 双形态、进度实时看板） |
| Agent | 配置化 + 五种官方 workflow | `agent_definition` 零代码配置 ReAct Agent；Chain/Routing/Parallelization/Orchestrator-Workers/Evaluator-Optimizer 编排器（虚拟线程并行）；插件子代理以目录层级只读展示 |
| CodeAgent | 编码智能体 | 项目（工作区）体系 + Plan/Ask/Auto/**Bypass** 四模式（按会话记忆、轮内切换即时生效）+ 审批门；双根沙箱（工作区读写 + 家目录只读）；系统原生目录选择器（macOS Finder） |
| RAG 知识库 | 五件套运营闭环 | Tika 解析 → Markdown 结构感知切片（标题/围栏/表格边界）→ 异步向量化（任务/进度/重试）→ 分段双写（可编辑真源）→ 命中测试（测-看-改） |
| 外部知识库 | 三 API 固定接入模板 | 心跳/库信息/向量查询；AgentX 本端向量化后跨服务检索，与本地库按分共排；启停开关完全解耦，引用卡片可定位到源文档章节与行号 |
| MCP | 双侧 | client：配置表驱动动态接入第三方（STDIO / Streamable-HTTP）+ 插件贡献映射；server：`@McpTool` 模板应用，企业包装存量系统的范本 |
| 鉴权 | JWT + RBAC | ADMIN / USER 两级，数据按用户隔离（越权 404）；新用户默认启用 |
| 可观测 | Micrometer（Spring AI 内建） | token 用量 / 调用延迟 / 向量检索 / 工具执行 span；OTLP 导出配置模板；业务级统计报表（含按日柱状图） |

## 产品定位

- **一机一用户的本地 app**：不做多租户服务端。配置类数据（技能/插件/本地工具根目录）走 `~/.agentx/` 本地目录，不入库——换机迁移拷目录即可，换新机不丢配置。
- **三级执行模型**：普通对话与本地项目 = 本机直接执行（沙箱 + 审批）；未来接入 GitHub 远程仓库的项目才引入容器沙箱。
- 浏览器仅是 UI：后端与用户同机，所以能调起系统原生目录选择器、真实执行技能脚本。

## 架构

```
┌────────────  agentx-web (React 18 + TS + Tailwind v4 + shadcn/ui)  ─────────────┐
│ 对话区（流式/思考/工具/审批/提问/任务清单/引用） │ 项目 │ 技能 │ 插件 │ 知识库 │ 设置 │
└───────────────────────────────┬──────────────────────────────────────────────────┘
                          REST + SSE (JWT)
┌───────────────────────────────┴────────────────────  agentx-server（唯一启动器）─┐
├──────────┬──────────┬─────────┬─────────┬──────────┬─────────┬─────────┬────────┤
│agentx-   │agentx-   │agentx-  │agentx-  │agentx-   │agentx-  │agentx-  │agentx- │
│chat      │agent     │rag      │coding   │skill     │plugin   │mcp      │tools   │
│会话/流式  │编排/派遣  │知识库    │编码/本地 │技能体系   │插件体系  │MCP client│工具中心 │
├──────────┴──────────┴─────────┴─────────┴──────────┴─────────┴─────────┴────────┤
│  agentx-infra-ai —— 模型屏蔽层（唯一模型出口）                                     │
│  ChatClientFactory / EmbeddingModelFactory（Provider SPI + 缓存 + 事件驱逐）      │
│  SseEvent 信封 / ChatStreamCustomizer SPI / UserPromptTransformer SPI            │
│  ApprovalRegistry / QuestionRegistry（阻塞式人机交互）/ AES-GCM / 调用审计         │
├──────────────────────────────────────────────────────────────────────────────────┤
│  Spring AI 2.0（ChatClient/Advisors/ToolCalling/Modular RAG/ChatMemory/MCP）      │
├───────────────┬──────────────────────┬───────────────────────────────────────────┤
│ PostgreSQL 17 │ PGVector（同库）      │ DeepSeek / 通义 / Ollama / 第三方 MCP      │
└───────────────┴──────────────────────┴───────────────────────────────────────────┘

本地目录：~/.agentx/{skills, plugins, master.key}    独立部署：agentx-mcp-server（:8090）
```

**分层铁律**：业务模块不触碰任何 provider SDK，一律经 `agentx-infra-ai` 的工厂获取模型客户端——换模型供应商不改业务代码。模块间扩展点全部走 SPI（`ChatModelProvider` / `ToolSource` / `ChatStreamCustomizer` / `UserPromptTransformer` / `SkillProvider` / `Workflow`），新增实现零改动既有代码。

**流式定制链**（`ChatStreamCustomizer` 按 `@Order` 串联）：Agent(10) → LocalTools(14) → Coding/Plan(15) → File(16) → Question(17) → SkillAutoTrigger(18) → AgentDispatch(19) → RAG(20)。`spec.toolCallbacks` 累加、`spec.system` 覆盖语义（最后生效）。

## 技术栈

| 维度 | 选型 |
|---|---|
| 核心 | Spring AI **2.0.0 GA** · Spring Boot **4.1** · JDK **21**（虚拟线程） |
| 数据 | PostgreSQL 17 + PGVector（业务 + 向量一库两用）· Flyway（V1–V18）· Spring Data JPA |
| 前端 | React 18 · TypeScript(strict) · Vite · Tailwind CSS v4 · shadcn/ui（Radix 原语手写）· lucide-react · sonner · zustand · Inter / JetBrains Mono |
| 构建 | Maven 多模块（13 模块） |

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

> 修改共享模块代码后需 `mvn install` 再重启 server——`spring-boot:run -pl agentx-server` 从本地仓库解析兄弟模块，仅 `compile` 不会生效。

### Docker 部署

```bash
cp .env.example .env    # 必填 AGENTX_JWT_SECRET 与 AGENTX_MASTER_KEY（openssl rand -base64 32）
docker compose up -d --build
# web :80 · server :8080 · mcp-server 模板 :8090 · postgres(pgvector)
```

注意：容器化部署会失去「本地 app」特性（原生目录选择器、读写宿主机文件、技能脚本执行受限于容器文件系统）。

### 测试

```bash
mvn verify                      # 后端测试（需本地 PG 的 agentx_test 库）
cd agentx-web && npm run build  # 前端构建 + tsc 严格检查
```

## 目录与模块

| 模块 | 职责 | 关键类 |
|---|---|---|
| `agentx-common` | 统一响应/异常/UUIDv7 | `ApiResponse` `BizException` `UuidV7` |
| `agentx-infra-ai` | 模型屏蔽层 + 流式基建 | `ChatClientFactory` `ChatModelProvider(SPI)` `SseEvent` `ChatStreamCustomizer(SPI)` `UserPromptTransformer(SPI)` `ApprovalRegistry` `QuestionRegistry` `ApiKeyCrypto` `AiCallAuditor` |
| `agentx-auth` | JWT/RBAC/用户管理 | `JwtService` `SecurityConfig` `@CurrentUser` `UserAdminController` |
| `agentx-tools` | 工具注册中心 + 内置工具 | `ToolRegistry` `ToolSource(SPI)` `@AgentTool`；`PlanTools`（任务清单）`AskUserQuestionTools` `WebTools`（webFetch/webSearch）示例工具 |
| `agentx-chat` | 会话双轨 + 流式主链路 | `ChatStreamService`（SSE 无超时）`ConversationService` `QuestionController` `ChatMemoryConfig` |
| `agentx-agent` | Agent 编排 + 派遣 | `AgentStreamCustomizer` `PlanStreamCustomizer` `QuestionStreamCustomizer` `AgentDispatchCustomizer`+`DispatchAgentTool` `PluginAgentRegistry` `WorkflowRunner` + 五种 `Workflow` |
| `agentx-rag` | 知识库（本地 + 外部） | `RagIngestService` `MarkdownStructureSplitter` `HitTestService` `RagStreamCustomizer` `ExternalKbService` `VectorStoreFactory` |
| `agentx-skill` | 技能体系（本地目录化） | `SkillFileStore`（`~/.agentx/skills/`）`SkillMarkdown`（frontmatter）`SkillExpansionService`（`/` 展开）`SkillAutoTriggerCustomizer`（M2 自动触发）`SkillResourceService`+`ReadSkillFileTool`（L3） |
| `agentx-plugin` | 插件体系（兼容 Claude Code 插件格式） | `PluginRegistry`（`~/.agentx/plugins/`）`MarketplaceService` `PluginService`（安装/更新/能力同步）`GitFetcher` `ManifestReader` `PluginSkillProvider` |
| `agentx-coding` | 编码智能体 + 本地天然工具 | `ShellTools`（FATAL/STRICT 双层黑名单）`WorkspaceReadTools/EditTools` `GitTools` `PathSandbox`（双根 + BYPASS unrestricted）`ApprovalGate`（无限期等待）`CodingModeRegistry`（轮内实时模式）`LocalToolsCustomizer`（普通对话本地工具）`DirectoryBrowseController`（原生目录选择器） |
| `agentx-mcp` | MCP client | `McpConnectionManager` `McpToolSource(L2)` `PluginMcpRegistry`（插件映射，默认停用） |
| `agentx-mcp-server` | MCP server 模板 | `@McpTool/@McpResource/@McpPrompt` 三件套（见其 README） |
| `agentx-server` | 唯一启动器 | Flyway 迁移（V1–V18）、全局装配 |
| `agentx-web` | 前端 | 见 `agentx-web/README.md` |

## SSE 流式协议（前后端契约）

`POST /api/v1/chat/stream`，每帧 `data:` 为单层 JSON：

```jsonc
{"type":"meta","conversationId":"…","messageId":"…"}      // 首帧
{"type":"text-delta","delta":"…"}                          // 正文增量
{"type":"reasoning","delta":"…"}                           // 思考增量（deepseek-reasoner 等）
{"type":"tool-call","id":"…","name":"…","args":"…","kind":"shell","preview":"…"}   // kind/preview 供前端富渲染；updatePlan 的 args 即任务清单全量
{"type":"tool-result","id":"…","name":"…","result":"…","kind":"…","preview":"…"}   // 工具返回
{"type":"approval-request","approvalId":"…","toolName":"…","kind":"shell","preview":{…}}  // 审批门：阻塞等待批准/拒绝
{"type":"approval-result","approvalId":"…","outcome":"approved|rejected|expired"}          // 审批权威终态
{"type":"question-request","questionId":"…","questions":[{question,header,options:[{label,description,preview}],multiSelect}]}  // askUserQuestion 提问卡
{"type":"question-result","questionId":"…","outcome":"answered|expired","answers":"…"}     // 提问权威终态
{"type":"rag-source","sources":[{docId,docName,segmentId,score,snippet,path?,headings?,startLine?,endLine?}]}
{"type":"done","usage":{promptTokens,completionTokens},"finishReason":"stop"}
{"type":"error","code":"…","message":"…"}                  // 业务错误走帧不断流
```

- `done`/`error` 是终止信号：前端收到即复原发送按钮并中止连接，不依赖服务端关流。
- **SSE 不设超时**（emitter timeout=0）：长 agent 轮次 + 人机阻塞（审批/提问）随时可能超过任何固定时长；客户端断开由 onError/onCompletion 兜底回收（未决审批/提问随流终止取消）。
- 审批/提问卡按请求时刻的**内容锚点**嵌入正文流：等待时天然在消息末尾，作答后新内容长在卡片下方随文融入。
- 反代注意：SSE 端点必须关缓冲/gzip、拉长超时——见 `agentx-web/nginx.conf` 与 `deploy/nginx-sse.conf.example`。

## Skill 技能

技能是放在 `~/.agentx/skills/` 的本地 Markdown（扁平 `<name>.md` 或目录 `<name>/SKILL.md`），frontmatter 兼容 Claude Code（`description` / `argument-hint` / `user-invocable` / `disable-model-invocation`）。

- **两种触发**：输入框 `/name args` 斜杠命令展开（`$ARGUMENTS`/`$1..$9` 替换，`/` 补全菜单支持键盘导航与 `plugin:skill` 命名空间）；模型按 L1 目录描述自动触发（`skill` 工具按需加载 L2 正文）。
- **渐进披露**：L1 元数据清单 → L2 正文 → L3 资源（`readSkillFile` 读 references/*.md；scripts/ 由资源公告携带绝对基准目录，模型经 `runShell` + 审批本地真实执行）。
- **双轨存储**：会话历史保留用户原文 `/name args`，展开文本只进模型上下文与记忆。
- 管理页支持增删改查与 `user-invocable`（是否进 `/` 菜单）/`model-invocable`（是否可被模型自动触发）开关。

## Plugin 插件（兼容 Claude Code 插件格式）

```bash
# 界面操作：插件页 → 添加 Marketplace → 输入 owner/repo（如 obra/superpowers）→ 安装
```

- **市场**：github `owner/repo` / git URL / 本地绝对路径；浅克隆缓存于 `~/.agentx/plugins/cache/`，一键更新（fetch + reset）。
- **插件能力映射**：技能 → `/plugin:skill` 进补全菜单与自动触发；子代理 → 同步为只读 Agent（Agent 页目录层级折叠展示，可被 `dispatchAgent` 派遣或建会话时选用）；`.mcp.json` → 同步为 MCP 配置但**默认停用**（信任边界：用户在 MCP 页显式启用）。
- hooks 不加载（AgentX 的 M2 自动触发覆盖其主要用途），插件页徽章明示未启用的能力。

## 人机交互三件套

- **askUserQuestion 提问卡**：模型需要用户拍板时呈现可点选卡片——单选（序号前置、选中裸勾、先选后提交）/ 多选 / 预览选择器（选项带 preview 时切换左选项右预览并排布局）/ 「其他」自由输入 / 链式多问分步作答。
- **审批门**：危险操作（shell/写文件/apply patch）在 Ask 模式逐操作审批，卡片富渲染命令与 diff 预览。
- **任务清单**：`updatePlan` 内置完整使用指南与正反示例；content（祈使形）/activeForm（进行时形）双形态，输入框上方紧凑面板实时推进，折叠单行展示「正在……」。
- 三者均**无限期等待用户**：阻塞的是虚拟线程（挂起仅几 KB 堆内存），等待期间不占用任何模型连接；终止条件只有用户回应或会话流终止。

## CodeAgent 与本地工具

对话页选择「项目」进入编码模式；**不选项目的普通对话也拥有本地天然工具**（文件/命令/网络），这是本地 app 定位的核心体验。

- **项目（工作区）**：新建空白项目（自动 `git init`）或经 macOS 原生目录选择器指向现有文件夹；侧栏按项目分组会话。
- **四模式**（按会话记忆、轮内切换即时生效，切 Auto/Bypass 自动批准未决审批）：
  - `Plan` 只读规划，写/执行工具不注册；
  - `Ask` 逐操作审批（默认，最安全）；
  - `Auto` 免审批直执行（沙箱边界与命令黑名单仍生效）;
  - `Bypass` 完全放行：无审批、无路径边界，命令黑名单仅保留毁机级保护（rm -rf / 、fork bomb、mkfs、dd 写盘、shutdown）。
- **双根沙箱**：编码会话读写限定工作区，只读工具可越出到家目录（本机文件参考）；`~` 展开、符号链接逃逸校验、macOS `/var→/private/var` 真实路径兜底。
- **安全须知**：模型会在本机真实执行命令与写文件——`Bypass` 等同放开一切防线，仅在完全信任任务时使用；服务器部署场景务必回退 `Ask` 并限制 `projects-root`。

## 外部知识库接入

任何知识库系统实现**三个 HTTP API**（心跳 / 库信息 / 向量查询）即可被接入：AgentX 用本端默认 EMBEDDING 模型把问题向量化，携查询向量跨服务检索，命中与本地库结果按相似度合并注入上下文；引用来源卡片可定位到源文档的文件路径、章节链与行号区间。

- 配置入口：设置 → 外部知识库；「测试连接」自动比对两侧 embedding 模型一致性；启停开关即完全解耦。
- 完整协议规范与 curl 自测见应用内「对接文档」（`ExternalKbGuide`）。
- 硬性前提：外部库建索引的 embedding 模型必须与 AgentX 默认 EMBEDDING 一致。

## 迭代指南

| 想加什么 | 怎么加 | 改动范围 |
|---|---|---|
| 技能 | `~/.agentx/skills/` 放一个带 frontmatter 的 Markdown（或技能页新建） | 零代码 |
| 插件 | 插件页添加 marketplace 后安装；自研插件按 Claude Code 插件格式建仓库 | 零代码 |
| 业务工具 | 写个类打 `@AgentTool`，方法打 `@Tool` | 新增一个类，自动进注册中心 |
| 业务 Agent | 管理后台配置（prompt + 工具 + 知识库 + workflow 类型） | 零代码 |
| 复杂编排 | 实现 `Workflow` 接口注册为 bean | agentx-agent 新增一个类 |
| 内部系统接入 | 复制 `agentx-mcp-server` 模板包一层，管理后台一配即入 | 新模块/新仓库 |
| 已有知识库接入 | 按三 API 模板实现心跳/库信息/向量查询，设置页一配即用 | 对方系统 ~100 行，AgentX 零改动 |
| 新模型供应商 | OpenAI 兼容的只加一条配置；私有协议实现 `ChatModelProvider` | infra-ai 一个类 |
| 业务领域模块 | 新建 `agentx-xxx`，依赖 infra-ai/tools | parent pom 加一行 |

## 安全与生产清单

- `AGENTX_JWT_SECRET`、`AGENTX_MASTER_KEY`（api-key 加密主密钥）生产必须显式设置；本地开发缺省时自动持久化开发密钥到 `~/.agentx/master.key`。
- 首启后立即修改 admin 密码（或经 `AGENTX_ADMIN_PASSWORD` 预设强密码）。
- 本地工具/CodeAgent 会在本机真实执行命令——`Bypass` 模式无路径边界无审批，慎用；服务器部署务必默认 `Ask`。
- 内置 `SqlQueryTools` 演示三道防线（白名单/语句形态/行数上限），生产建议叠加只读库账号。
- 插件的 MCP server 默认停用（信任边界），启用前确认其 command/url 可信；MCP server 端点不要裸暴露公网。
- 外部知识库接入模板未定义鉴权头（面向内网/本机），暴露公网需反向代理加鉴权。

## 可观测

Spring AI 内建 Micrometer 指标开箱即得：`gen_ai_client_operation_seconds`、`gen_ai_client_token_usage_total`、`db_vector_client_operation_seconds`、`execute_tool <name>` span。对接 OTLP（Grafana / Langfuse）取消 `application.yml` 中注释即可。业务级报表走 `/api/v1/admin/stats/*`（总量/按日/按模型，OpenAI 兼容端点的用量从终帧空 choices 分片正确提取）。

## 设计文档

完整设计文档与各里程碑实现计划维护在项目外部知识库（`2026-07-12-agentx-design.md`、M1–M6 计划、skill/plugin 调研与设计文档），含技术选型论证、调研结论、数据模型与关键流程。

## License

MIT

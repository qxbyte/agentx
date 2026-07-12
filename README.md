# AgentX — Spring AI 企业级智能体平台

基于 **Spring AI 2.0.0** 的企业级智能体底座：ChatGPT 风格对话、工具调用（Tool Calling）、Agent 编排（ReAct + 五种 workflow 模式）、RAG 知识库、MCP 双侧接入。企业业务在此之上以「新增工具 / 新增 Agent / 新增知识库 / 新增模块」的方式迭代。

## 技术栈

| 维度 | 选型 |
|---|---|
| 核心 | Spring AI 2.0.0 GA · Spring Boot 4.1 · JDK 21（虚拟线程） |
| 数据 | PostgreSQL 17 + PGVector（业务表 + 向量一库两用）· Flyway |
| 鉴权 | Spring Security + JWT + RBAC（ADMIN / USER） |
| 前端 | React 18 + TypeScript + Vite（`agentx-web`，M2 起） |
| 模型 | 默认 DeepSeek；通义等 OpenAI 兼容端点；Ollama 私有化 |

## 模块

| 模块 | 职责 |
|---|---|
| `agentx-common` | 统一响应 / 异常体系 / UUIDv7 |
| `agentx-infra-ai` | 模型屏蔽层：模型配置、api-key 加密、ChatClient 工厂（M2） |
| `agentx-auth` | 用户 / JWT / 安全链 / RBAC |
| `agentx-server` | 唯一启动模块：装配 + REST API + Flyway |

设计文档与实现计划：`~/Documents/Obsidian/Notes/07-Ideas/agentx/`（不入库）。

## 本地开发

```bash
# 依赖：brew install openjdk@21 maven postgresql@17 pgvector
brew services start postgresql@17
# 首次建库：见 docs（CREATE ROLE agentx / CREATE DATABASE agentx + agentx_test / CREATE EXTENSION vector）

JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl agentx-server spring-boot:run
```

默认管理员 `admin / admin123`（环境变量 `AGENTX_ADMIN_PASSWORD` 覆盖，生产必改）。

```bash
# 冒烟
curl -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
```

## Docker 部署

```bash
cp .env.example .env   # 填 AGENTX_JWT_SECRET 与 AGENTX_MASTER_KEY（openssl rand -base64 32）
docker compose up -d --build
```

## 运维注意

- SSE 流式端点经 nginx 反代时必须关闭缓冲，见 `deploy/nginx-sse.conf.example`。
- `AGENTX_MASTER_KEY` 是 api-key 加密主密钥：不设置时启动用随机密钥（重启后已存密文不可解），**仅限开发**。

## 测试

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify   # 需本地 PG 的 agentx_test 库
```

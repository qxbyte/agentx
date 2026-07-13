# agentx-web

AgentX 企业级智能体平台的 React 前端（ChatGPT 风格界面）。

技术栈：Vite + React 18 + TypeScript + Ant Design v5 + zustand + react-router-dom v6 + axios + @microsoft/fetch-event-source + react-markdown。

## 启动

```bash
npm install
npm run dev        # http://localhost:5173
```

开发环境要求后端运行在 `http://localhost:8080`（见下方代理说明）。

## 构建

```bash
npm run build      # tsc -b 类型检查 + vite build，产物输出到 dist/
npm run preview    # 本地预览构建产物
npx tsc --noEmit   # 单独跑类型检查
```

## 代理说明

`vite.config.ts` 中配置了开发代理：所有 `/api` 请求转发到 `http://localhost:8080`。

- REST 接口统一走 `src/api/` 下的 axios 实例（自动携带 Bearer token，401 时用 refreshToken 静默刷新并重放请求，刷新失败跳转 `/login`）。
- 流式对话走 `POST /api/v1/chat/stream`（SSE），由 `src/sse/streamChat.ts` 用 `@microsoft/fetch-event-source` 消费，事件类型定义见 `src/sse/events.ts`。

生产部署时需在网关/Nginx 层将 `/api` 路由到后端服务。

## 路由清单

| 路径 | 页面 | 说明 |
| --- | --- | --- |
| `/login` | 登录页 | 无需登录 |
| `/` | 对话（空会话欢迎态） | 需登录 |
| `/c/:conversationId` | 对话详情（SSE 流式） | 需登录 |
| `/kb` | 知识库列表（卡片网格，新建/编辑/删除） | 需登录 |
| `/kb/:kbId` | 知识库详情（文档上传与分段管理 / 命中测试 / 设置） | 需登录 |
| `/admin` | 管理后台（重定向到 `/admin/models`） | 仅 ADMIN，非 ADMIN 重定向 `/` |
| `/admin/models` | 模型配置（增删改、默认星标、启停） | 仅 ADMIN |
| `/admin/mcp` | MCP 服务（STDIO / Streamable HTTP、测试连接） | 仅 ADMIN |
| `/admin/tools` | 工具目录（source、Schema 查看、启停） | 仅 ADMIN |
| `/admin/agents` | Agent 管理（系统提示词、工作流、工具/知识库绑定） | 仅 ADMIN |
| `/admin/users` | 用户管理（新建、启停） | 仅 ADMIN |
| `/admin/stats` | 用量统计（汇总卡片、近 14 天柱状图、按模型表格） | 仅 ADMIN |

其余路径一律重定向 `/`。守卫逻辑见 `src/App.tsx`（`RequireAuth` / `RequireAdmin`）。

## 目录结构

```
src/
├── api/          # axios 实例、token 存取、auth / chat / kb / agents / admin 域 API
├── sse/          # SSE 事件类型（discriminated union）+ streamChat 封装
├── stores/       # zustand：auth（登录态）、chat（会话/消息/流式）
├── components/   # Sidebar（含主导航）、AppShell（内容页骨架）、NewChatModal、
│                 # ChatInput、MessageItem、MarkdownRenderer、
│                 # ReasoningBlock、ToolCallCard、SourceBadge、Logo
├── pages/        # Login、Chat
│   ├── kb/       # KbListPage、KbDetailPage（DocumentsTab / HitTestTab /
│   │             # SettingsTab、SegmentsDrawer、KbConfigFormItems）
│   └── admin/    # AdminLayout + Models / Mcp / Tools / Agents / Users / Stats
├── hooks/        # useIsMobile（≤768px 侧栏抽屉化）
├── theme.ts      # antd 主题 token（浅色；暗色预留出口）
└── index.css     # 全局设计 token（CSS 变量）与组件样式
```

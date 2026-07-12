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

## 目录结构

```
src/
├── api/          # axios 实例、token 存取、auth / chat 域 API
├── sse/          # SSE 事件类型（discriminated union）+ streamChat 封装
├── stores/       # zustand：auth（登录态）、chat（会话/消息/流式）
├── components/   # Sidebar、ChatInput、MessageItem、MarkdownRenderer、
│                 # ReasoningBlock、ToolCallCard、SourceBadge、Logo
├── pages/        # Login、Chat
├── hooks/        # useIsMobile（≤768px 侧栏抽屉化）
├── theme.ts      # antd 主题 token（浅色；暗色预留出口）
└── index.css     # 全局设计 token（CSS 变量）与组件样式
```

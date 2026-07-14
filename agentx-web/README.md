# agentx-web

AgentX 企业级智能体平台的 React 前端（ChatGPT 风格界面 + CodeAgent 编码工作台）。

技术栈：Vite + React 18 + TypeScript(strict) + Tailwind CSS v4 + shadcn/ui（基于 Radix 原语手写）+ lucide-react + sonner + zustand + react-router-dom v6 + axios + @microsoft/fetch-event-source + react-markdown。字体 Inter / JetBrains Mono（`@fontsource-variable`）。

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
- 流式对话走 `POST /api/v1/chat/stream`（SSE），由 `src/sse/streamChat.ts` 用 `@microsoft/fetch-event-source` 消费，事件类型定义见 `src/sse/events.ts`（判别联合）。`done`/`error` 帧为终止信号，收到即复原发送态并中止连接。

生产部署时需在网关/Nginx 层将 `/api` 路由到后端服务。

## UI 体系（Tailwind v4 + shadcn/ui）

- **样式令牌**：`src/styles/theme.css` 用 Tailwind v4 的 CSS-first `@theme` 定义 shadcn 变量，映射自 `--ax-*` 设计令牌；`src/index.css` 保留 `.ax-*` 旧设计系统类以保证视觉零回归。
- **组件**：`src/components/ui/` 下手写 shadcn 组件（button/dialog/select/popover/tooltip/dropdown-menu/sheet/tabs/table/switch/multi-select…），基于 `@radix-ui/*` 原语 + `cva` + `cn()`。toast 用 sonner（unstyled，样式在 index.css 覆盖为顶部胶囊）。
- 图标统一 lucide-react；无 antd 依赖。

## 路由清单

| 路径 | 页面 | 说明 |
| --- | --- | --- |
| `/login` | 登录页 | 无需登录 |
| `/` | 对话（空会话欢迎态） | 需登录 |
| `/c/:conversationId` | 对话详情（SSE 流式；选项目后进入 CodeAgent 模式） | 需登录 |
| `/kb` | 知识库列表（卡片网格，新建/编辑/删除） | 需登录 |
| `/kb/:kbId` | 知识库详情（文档上传与分段管理 / 命中测试 / 设置） | 需登录 |
| `/workspaces` | 项目（工作区）列表 | 需登录 |
| `/admin` | 管理后台（重定向到 `/admin/models`） | 仅 ADMIN，非 ADMIN 重定向 `/` |
| `/admin/models` | 模型配置（增删改、默认星标、启停） | 仅 ADMIN |
| `/admin/mcp` | MCP 服务（STDIO / Streamable HTTP、测试连接） | 仅 ADMIN |
| `/admin/tools` | 工具目录（source、Schema 查看、启停） | 仅 ADMIN |
| `/admin/agents` | Agent 管理（系统提示词、工作流、工具/知识库绑定） | 仅 ADMIN |
| `/admin/external-kbs` | 外部知识库接入（服务地址/vault/topK、测试连接、对接文档） | 仅 ADMIN |
| `/admin/users` | 用户管理（新建、启停） | 仅 ADMIN |
| `/admin/stats` | 用量统计（汇总卡片、近 14 天柱状图、按模型表格） | 仅 ADMIN |

其余路径一律重定向 `/`。守卫逻辑见 `src/App.tsx`（`RequireAuth` / `RequireAdmin`）。管理后台入口与退出按钮收在点击用户头像的弹出菜单里（ChatGPT 式）。

## 目录结构

```
src/
├── api/          # axios 实例、token 存取、auth / chat / kb / agents / coding / externalKb / admin 域 API
├── sse/          # SSE 事件类型（判别联合，含 tool-call/tool-result/approval-request）+ streamChat 封装
├── stores/       # zustand：auth（登录态）、chat（会话/消息/流式 + CodeAgent 状态 + 项目列表）
├── components/
│   ├── ui/       # 手写 shadcn 组件（Radix 原语 + cva）
│   ├── coding/   # CodeAgent：InputToolbar / ProjectPicker / KbPicker / DiffView /
│   │             # ShellOutput / ApprovalCard / ToolResultCard / WorkspaceFormDialog…
│   ├── Sidebar / ChatInput / MessageItem / MarkdownRenderer /
│   ├── ReasoningBlock / ToolCallCard / ToolCallGroup / SourceBadge / Logo …
├── pages/        # Login、Chat
│   ├── coding/   # WorkspacesPage（项目列表）
│   ├── kb/       # KbListPage、KbDetailPage（DocumentsTab / HitTestTab / SettingsTab …）
│   └── admin/    # AdminLayout + Models / Mcp / Tools / Agents / ExternalKb(+Guide) / Users / Stats
├── lib/          # parseDiff 等纯函数、cn()
├── hooks/        # useIsMobile（≤768px 侧栏抽屉化）
├── styles/       # theme.css（Tailwind v4 @theme + shadcn 变量）
├── index.css     # 全局设计 token（CSS 变量）、.ax-* 旧设计系统类、sonner 覆盖
└── main.tsx      # 入口（字体 + 样式挂载）
```

## CodeAgent 前端

对话页选择项目后，`ChatInput` 展开内嵌工具条（`components/coding/InputToolbar`）：模型选择、Plan/Ask/Auto 模式段选、项目选择器。工具帧按 `kind/preview` 分发到 `DiffView`（unified diff）、`ShellOutput`（终端）、`ApprovalCard`（审批门交互）、`ToolResultCard`。连续工具调用折叠为 `ToolCallGroup`。知识库为对话创建时属性：仅新建对话首次发消息前可选，项目内新建的对话锁定沿用项目归属与知识库。

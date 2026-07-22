/** 后端统一响应信封，code === 0 表示成功 */
export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

export type MessageRole = 'USER' | 'ASSISTANT' | 'TOOL' | 'SYSTEM'

export interface User {
  id: string
  username: string
  nickname: string
  role: string
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  user: User
}

export interface Conversation {
  id: string
  title: string
  agentId: string | null
  modelConfigId: string | null
  /** CodeAgent：会话归属的项目（工作区）；null 为普通对话。侧栏据此分组。 */
  workspaceId: string | null
  /** updatePlan 工具最近一次调用的参数原文（JSON 字符串），恢复计划面板用 */
  planState?: string | null
  createdAt: string
  updatedAt: string
}

/** 消息附件元数据（chat_message.attachments，历史气泡芯片渲染用） */
export interface AttachmentMeta {
  id: string
  filename: string
  /** text | image（旧数据可能缺省，按 text 处理） */
  kind?: string
  sizeBytes: number
}

/** 输入框待发送附件（上传即解析；ready 后才可随消息发送） */
export interface PendingAttachment {
  /** 本地临时 key（上传前生成，贯穿生命周期） */
  key: string
  /** 服务端附件 id（ready 后存在） */
  id?: string
  filename: string
  relPath?: string
  /** text | image */
  kind: 'text' | 'image'
  /** 图片的本地预览 blob URL（发送/移除时 revoke） */
  previewUrl?: string
  sizeBytes: number
  truncated?: boolean
  status: 'uploading' | 'ready' | 'failed'
  error?: string
}

/** 计划步骤状态（与后端 PlanTools 约定一致） */
export type PlanStepStatus = 'pending' | 'in_progress' | 'completed'

export interface PlanStep {
  step: string
  /** 进行时形态（activeForm）：该步执行中对用户展示，如「正在运行测试」 */
  activeForm?: string
  status: PlanStepStatus
}

/** 模型 updatePlan 调用的归一化结构：输入框上方任务面板的数据源。
    新 shape 为 todos[{content,activeForm,status}]，旧持久化数据为 {title,steps[{step,status}]}，
    parsePlanState 统一归一到本结构 */
export interface PlanState {
  /** 旧数据的计划标题；新 shape 无标题，面板回落「任务清单」 */
  title?: string | null
  steps: PlanStep[]
  explanation?: string | null
}

export interface RagSource {
  docId: string
  docName: string
  segmentId: string
  score: number
  snippet: string
  /** 来源定位（可选，外部知识库命中携带）：文件路径 / 章节链 / 原文行号区间 */
  path?: string | null
  headings?: string[] | null
  startLine?: number | null
  endLine?: number | null
}

export interface ToolCallInfo {
  id: string
  name: string
  args?: unknown
  /** tool-result 事件到达后填充；undefined 表示仍在执行 */
  result?: unknown
  done?: boolean
  /** CodeAgent：结构化预览类型（patch/shell/write/commit/read/grep…），前端按此分发富渲染 */
  kind?: string
  /** CodeAgent：tool-call 携带的结构化预览（diff/command 等） */
  preview?: ApprovalPreview
}

/** 消息本体的有序 block（对齐 Claude transcript：渲染即回放） */
export type MessageBlock =
  | { type: 'reasoning'; text: string }
  | ({ type: 'tool' } & ToolCallInfo)

export interface TokenUsage {
  promptTokens: number
  completionTokens: number
}

export interface MessageError {
  code?: string
  message: string
}

/* ============================================================
   知识库
   ============================================================ */

export interface KnowledgeBase {
  id: string
  name: string
  description: string | null
  chunkSize: number
  chunkOverlap: number
  topK: number
  similarityThreshold: number
  embeddingModelId?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface KbPayload {
  name: string
  description?: string
  chunkSize: number
  chunkOverlap: number
  topK: number
  similarityThreshold: number
  embeddingModelId?: string
}

export type DocStatus = 'UPLOADED' | 'PARSING' | 'INGESTING' | 'READY' | 'FAILED'

export interface DocView {
  id: string
  kbId: string
  filename: string
  mimeType: string
  sizeBytes: number
  status: DocStatus
  segmentCount: number
  createdAt: string
}

export type IngestTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface IngestTask {
  status: IngestTaskStatus
  /** 0-100 */
  progress: number
  errorMsg?: string | null
}

export interface SegmentView {
  id: string
  seqNo: number
  content: string
  charCount: number
  enabled: boolean
}

export interface HitTestResult {
  segmentId: string
  docId: string
  docName: string
  content: string
  score: number
}

/* ============================================================
   模型配置 / MCP / 工具 / Agent（管理后台）
   ============================================================ */

export type ProviderType = 'DEEPSEEK' | 'OPENAI_COMPATIBLE' | 'OLLAMA'
export type ModelType = 'CHAT' | 'EMBEDDING' | 'RERANK'

export interface ModelConfig {
  id: string
  name: string
  providerType: ProviderType
  baseUrl: string
  /** 后端仅回显掩码，明文 apiKey 只在提交时上送 */
  maskedApiKey?: string | null
  modelName: string
  type: ModelType
  enabled: boolean
  /** 与后端 ModelConfigDtos.View 字段名一致 */
  defaultModel?: boolean
}

export interface ModelConfigPayload {
  name: string
  providerType: ProviderType
  baseUrl: string
  /** 创建时必填；编辑留空表示不修改 */
  apiKey?: string
  modelName: string
  type: ModelType
  enabled: boolean
}

export type McpTransport = 'STDIO' | 'STREAMABLE_HTTP'

export interface McpServer {
  id: string
  name: string
  transport: McpTransport
  /** JSON 字符串；STDIO: {command,args,env}，HTTP: {url,headers} */
  connectParams: string | null
  enabled: boolean
}

export interface McpToolPreview {
  name: string
  description: string
}

export type ToolSource = 'CODE' | 'MCP'

export interface ToolView {
  name: string
  source: ToolSource
  description: string
  /** JSON Schema，可能是字符串或对象 */
  paramsSchema?: string | Record<string, unknown> | null
  enabled: boolean
}

export type WorkflowType =
  | 'REACT'
  | 'CHAIN'
  | 'ROUTING'
  | 'PARALLELIZATION'
  | 'ORCHESTRATOR_WORKERS'
  | 'EVALUATOR_OPTIMIZER'

export interface AgentView {
  id: string
  name: string
  description: string | null
  systemPrompt: string | null
  workflowType: WorkflowType
  /** 后端 View 返回 JSON 字符串（如 '["a","b"]'），使用前需 parse */
  toolNames: string | null
  /** 同上，JSON 字符串 */
  kbIds: string | null
  maxIterations: number
  enabled: boolean
  /** USER=管理端创建 / PLUGIN=插件贡献(只读) */
  source: string
  /** 插件贡献时的归属插件 id（如 task-swarm@qxbyte-hub）,USER 来源为 null */
  pluginId: string | null
}

export interface AgentPayload {
  name: string
  description?: string
  systemPrompt?: string
  workflowType: WorkflowType
  /** 提交时传数组（后端负责序列化） */
  toolNames: string[]
  kbIds: string[]
  maxIterations: number
  enabled: boolean
}

/* ============================================================
   用户管理 / 用量统计（管理后台）
   ============================================================ */

export type UserStatus = 'ACTIVE' | 'DISABLED'

export interface AdminUser {
  id: string
  username: string
  nickname: string
  role: string
  status: UserStatus
  createdAt?: string
}

export interface AdminUserPayload {
  username: string
  password: string
  nickname: string
  role: string
}

export interface TokenStatsSummary {
  total_calls: number
  prompt_tokens: number
  completion_tokens: number
  error_calls: number
}

export interface DailyTokenStat {
  date: string
  total_calls?: number
  prompt_tokens?: number
  completion_tokens?: number
  error_calls?: number
}

/** by-model 统计行：后端字段不固定，表格列动态推导 */
export type ModelTokenStat = Record<string, string | number | null>

/** 会话内的一条消息（服务端历史 + 客户端流式共用） */
export interface ChatMessage {
  id: string
  role: MessageRole
  content: string
  /** 有序 blocks：思考/工具按真实发生顺序（服务端为 JSON 字符串，normalizeMessage 解析） */
  blocks?: MessageBlock[] | null
  ragSources?: RagSource[] | null
  tokenUsage?: TokenUsage | null
  /** 用户消息附件元数据（服务端为 JSON 字符串，normalizeMessage 解析为数组） */
  attachments?: AttachmentMeta[] | null
  createdAt?: string
  /** 客户端状态：正在流式生成 */
  streaming?: boolean
  /** 客户端状态：流式过程中收到 error 事件 */
  error?: MessageError | null
  /** CodeAgent Ask 模式：待审批 / 已处理的操作卡 */
  approvals?: ApprovalItem[] | null
  /** askUserQuestion 工具：待作答 / 已作答的提问卡 */
  questions?: QuestionItem[] | null
}

/* ---------- askUserQuestion 提问交互 ---------- */

export interface QuestionOption {
  label: string
  description?: string | null
  /** 预览内容（代码/配置/示意等多行文本）：任一选项带预览时卡片切换为左选项右预览布局（仅单选） */
  preview?: string | null
}

export interface QuestionSpec {
  question: string
  header?: string | null
  options: QuestionOption[]
  multiSelect?: boolean
}

/** 提交给后端的单问答案（跳过时 skipped=true 且无 selected） */
export interface QuestionAnswer {
  question: string
  selected: string[]
  otherText?: string
  skipped?: boolean
}

export interface QuestionItem {
  questionId: string
  questions: QuestionSpec[]
  status: 'pending' | 'answered' | 'expired'
  answers?: QuestionAnswer[] | null
  /** 提问时刻的消息内容长度：卡片按此锚点嵌入正文流（等待时天然在末尾,答后新内容长在卡片下方）；
      历史重建无锚点时渲染在正文之后 */
  contentOffset?: number
}

/* ============================================================
   CodeAgent（编码智能体）
   ============================================================ */
export type CodingMode = 'PLAN' | 'ASK' | 'AUTO' | 'BYPASS'

export interface Workspace {
  id: string
  name: string
  rootPath: string
  kbId?: string | null
  createdAt?: string
}

export interface WorkspaceValidation {
  exists: boolean
  writable: boolean
  gitRepo: boolean
  message: string
}

/** 输入框模型选择器用（无密钥） */
export interface ModelOption {
  id: string
  name: string
  modelName: string
  defaultModel: boolean
}

export type ApprovalKind = 'patch' | 'shell' | 'write' | 'commit' | 'generic'

/** 审批 / 工具预览的结构化载荷，按 kind 取对应字段 */
export interface ApprovalPreview {
  diff?: string
  command?: string
  cwd?: string
  message?: string
  path?: string
  content?: string
  dangerous?: boolean
  args?: string
  [key: string]: unknown
}

/** expired：审批超时 / 会话结束 / 后端已无此项——卡片失效，不可再操作 */
export type ApprovalStatus = 'pending' | 'approved' | 'rejected' | 'expired'

/** 外部知识库（固定三 API 模板接入；enabled=false 检索完全跳过） */
export interface ExternalKb {
  id: string
  name: string
  baseUrl: string
  vaultId: string
  heartbeatPath: string
  infoPath: string
  searchPath: string
  topK: number
  similarityThreshold: number
  enabled: boolean
  createdAt?: string
}

export interface ExternalKbProbe {
  alive: boolean
  service?: string | null
  vaultName?: string | null
  embeddingModel?: string | null
  dims: number
  chunkCount: number
  error?: string | null
  /** embedding 模型不一致等提醒（非空即需注意） */
  warning?: string | null
}

export interface ApprovalItem {
  approvalId: string
  toolName: string
  kind: ApprovalKind | string
  preview: ApprovalPreview
  status: ApprovalStatus
  /** 同 QuestionItem.contentOffset：审批卡按请求时刻的内容长度锚定在正文流中 */
  contentOffset?: number
}

/* ---------- Skill 斜杠命令 ---------- */

/** 补全菜单元数据（渐进式披露 L1，不含 content） */
export interface SkillMeta {
  id: string
  name: string
  description: string
  argumentHint?: string | null
}

/** 管理列表视图（含启停与调用开关状态，不含 content） */
export interface SkillView {
  id: string
  name: string
  description: string
  argumentHint?: string | null
  enabled: boolean
  userInvocable: boolean
  modelInvocable: boolean
  createdAt: string
  updatedAt: string
}

/** 详情（含 content，编辑用） */
export interface SkillDetail {
  id: string
  name: string
  description: string
  argumentHint?: string | null
  content: string
  enabled: boolean
  userInvocable: boolean
  modelInvocable: boolean
}

export interface SkillPayload {
  name: string
  description: string
  argumentHint?: string
  content: string
  userInvocable?: boolean
  modelInvocable?: boolean
}

/* ---------- Plugin(本机目录化) ---------- */

export interface AvailablePlugin {
  name: string
  description: string
  version: string
  sourceType: string
  installed: boolean
  enabled?: boolean | null
}

export interface MarketplaceView {
  name: string
  sourceType: string
  locator: string
  installLocation: string
  plugins: AvailablePlugin[]
}

export interface InstalledPluginView {
  id: string
  name: string
  marketplace: string
  version: string
  description: string
  enabled: boolean
  installedAt: string
  skillCount: number
  agentCount: number
  mcpCount: number
  /** 暂不支持的能力名单,如 ["hooks"] */
  unsupported: string[]
}

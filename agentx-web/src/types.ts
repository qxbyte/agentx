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
  createdAt: string
  updatedAt: string
}

export interface RagSource {
  docId: string
  docName: string
  segmentId: string
  score: number
  snippet: string
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
export type ModelType = 'CHAT' | 'EMBEDDING'

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
  isDefault?: boolean
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
  reasoningContent?: string | null
  toolCalls?: ToolCallInfo[] | null
  ragSources?: RagSource[] | null
  tokenUsage?: TokenUsage | null
  createdAt?: string
  /** 客户端状态：正在流式生成 */
  streaming?: boolean
  /** 客户端状态：流式过程中收到 error 事件 */
  error?: MessageError | null
  /** CodeAgent Ask 模式：待审批 / 已处理的操作卡 */
  approvals?: ApprovalItem[] | null
}

/* ============================================================
   CodeAgent（编码智能体）
   ============================================================ */
export type CodingMode = 'PLAN' | 'ASK' | 'AUTO'

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

export type ApprovalStatus = 'pending' | 'approved' | 'rejected'

export interface ApprovalItem {
  approvalId: string
  toolName: string
  kind: ApprovalKind | string
  preview: ApprovalPreview
  status: ApprovalStatus
}

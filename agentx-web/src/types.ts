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
}

export interface TokenUsage {
  promptTokens: number
  completionTokens: number
}

export interface MessageError {
  code?: string
  message: string
}

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
}

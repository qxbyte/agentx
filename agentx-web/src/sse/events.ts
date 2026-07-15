import type { ApprovalPreview, RagSource, TokenUsage } from '../types'

/** SSE 流式协议事件（类型化 discriminated union） */
export type SseEvent =
  | { type: 'meta'; conversationId: string; messageId: string }
  | { type: 'text-delta'; delta: string }
  | { type: 'reasoning'; delta: string }
  | { type: 'tool-call'; id: string; name: string; args?: unknown; kind?: string; preview?: ApprovalPreview }
  | { type: 'tool-result'; id: string; name: string; result?: unknown; kind?: string }
  | { type: 'rag-source'; sources: RagSource[] }
  | {
      type: 'approval-request'
      approvalId: string
      toolName: string
      kind: string
      preview: ApprovalPreview
    }
  | { type: 'approval-result'; approvalId: string; outcome: 'approved' | 'rejected' | 'expired' }
  | { type: 'done'; usage?: TokenUsage; finishReason?: string }
  | { type: 'error'; code?: string; message: string }

const KNOWN_TYPES: ReadonlySet<string> = new Set([
  'meta',
  'text-delta',
  'reasoning',
  'tool-call',
  'tool-result',
  'rag-source',
  'approval-request',
  'approval-result',
  'done',
  'error',
])

/** 解析一行 event data（JSON）；非法或未知类型返回 null（向前兼容，静默忽略） */
export function parseSseEvent(data: string): SseEvent | null {
  if (!data) return null
  try {
    const parsed: unknown = JSON.parse(data)
    if (
      typeof parsed === 'object' &&
      parsed !== null &&
      'type' in parsed &&
      typeof (parsed as { type: unknown }).type === 'string' &&
      KNOWN_TYPES.has((parsed as { type: string }).type)
    ) {
      return parsed as SseEvent
    }
    return null
  } catch {
    return null
  }
}

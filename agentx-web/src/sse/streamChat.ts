import { fetchEventSource } from '@microsoft/fetch-event-source'
import { getAccessToken } from '../api/tokens'
import { parseSseEvent, type SseEvent } from './events'

export interface StreamChatParams {
  /** 省略时后端自动创建会话，并在 meta 帧返回新 conversationId */
  conversationId?: string
  content: string
  signal: AbortSignal
  onEvent: (event: SseEvent) => void
}

/** 不可重试的流式错误（fetch-event-source 默认会无限重连，必须抛出终止） */
export class StreamFatalError extends Error {
  status: number | undefined

  constructor(message: string, status?: number) {
    super(message)
    this.name = 'StreamFatalError'
    this.status = status
  }
}

/**
 * 发起流式对话。Promise 在流正常结束时 resolve；
 * 中断（AbortController）时抛出 AbortError；HTTP/网络错误抛出 StreamFatalError。
 */
export async function streamChat(params: StreamChatParams): Promise<void> {
  const { conversationId, content, signal, onEvent } = params
  const token = getAccessToken()

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }
  if (token) headers.Authorization = `Bearer ${token}`

  await fetchEventSource('/api/v1/chat/stream', {
    method: 'POST',
    headers,
    body: JSON.stringify(conversationId ? { conversationId, content } : { content }),
    signal,
    // 切到后台标签页时不中断流
    openWhenHidden: true,
    async onopen(response) {
      if (response.ok) return
      if (response.status === 401) {
        throw new StreamFatalError('登录已过期，请重新登录', 401)
      }
      throw new StreamFatalError(`连接失败（HTTP ${response.status}）`, response.status)
    },
    onmessage(message) {
      const event = parseSseEvent(message.data)
      if (event) onEvent(event)
    },
    onclose() {
      // 服务端主动关闭视为正常结束（done 事件应已先到达）
    },
    onerror(err) {
      // 直接抛出以禁用自动重连，由调用方统一处理
      throw err
    },
  })
}

import { fetchEventSource } from '@microsoft/fetch-event-source'
import { redirectToLogin, refreshAccessToken } from '../api/http'
import { getAccessToken } from '../api/tokens'
import { parseSseEvent, type SseEvent } from './events'

export interface StreamChatParams {
  /** 省略时后端自动创建会话，并在 meta 帧返回新 conversationId */
  conversationId?: string
  content: string
  /** 本次消息使用的模型（覆盖会话默认） */
  modelConfigId?: string
  /** 用户显式切回「默认模型」：后端据此清除会话固化的模型选择 */
  useDefaultModel?: boolean
  /** CodeAgent：绑定的工作区；非空即进入 coding 模式 */
  workspaceId?: string
  /** CodeAgent：Plan / Ask / Auto */
  mode?: string
  /** 本次检索追加的知识库（输入框多选） */
  kbIds?: string[]
  /** 本条消息携带的已上传附件 */
  attachmentIds?: string[]
  signal: AbortSignal
  onEvent: (event: SseEvent) => void
}

/** 内部哨兵：标记服务端正常关流。fetch-event-source 的 onclose 若正常返回
 *  会进入自动重连（并重发 POST！），必须抛异常终止，再在外层识别为正常结束。 */
class StreamClosedError extends Error {
  constructor() {
    super('stream closed')
    this.name = 'StreamClosedError'
  }
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
 *
 * 鉴权：本通道绕过 axios 拦截器，401 时复用同一套单飞刷新机制——
 * 刷新成功自动重试一次（401 发生在 onopen，尚无任何副作用，重试安全）；
 * 刷新失败（refresh token 也过期）跳登录页，与 axios 路径行为一致。
 */
export async function streamChat(params: StreamChatParams): Promise<void> {
  try {
    await runStream(params, getAccessToken())
  } catch (error) {
    if (error instanceof StreamFatalError && error.status === 401) {
      let token: string
      try {
        token = await refreshAccessToken()
      } catch {
        redirectToLogin()
        throw error
      }
      await runStream(params, token)
      return
    }
    throw error
  }
}

async function runStream(params: StreamChatParams, token: string | null): Promise<void> {
  const {
    conversationId,
    content,
    modelConfigId,
    useDefaultModel,
    workspaceId,
    mode,
    kbIds,
    attachmentIds,
    signal,
    onEvent,
  } = params

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }
  if (token) headers.Authorization = `Bearer ${token}`

  try {
    await fetchEventSource('/api/v1/chat/stream', {
    method: 'POST',
    headers,
    body: JSON.stringify({
      ...(conversationId ? { conversationId } : {}),
      content,
      ...(modelConfigId ? { modelConfigId } : {}),
      ...(useDefaultModel ? { useDefaultModel } : {}),
      ...(workspaceId ? { workspaceId } : {}),
      ...(mode ? { mode } : {}),
      ...(kbIds && kbIds.length > 0 ? { kbIds } : {}),
      ...(attachmentIds && attachmentIds.length > 0 ? { attachmentIds } : {}),
    }),
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
        // 服务端主动关闭：抛哨兵终止库的重连循环，外层识别为正常结束
        throw new StreamClosedError()
      },
      onerror(err) {
        // 直接抛出以禁用自动重连，由调用方统一处理
        throw err
      },
    })
  } catch (error) {
    if (error instanceof StreamClosedError) {
      return // 正常结束
    }
    throw error
  }
}

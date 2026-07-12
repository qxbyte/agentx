import { create } from 'zustand'
import * as chatApi from '../api/chat'
import { extractErrorMessage } from '../api/http'
import type { SseEvent } from '../sse/events'
import { streamChat } from '../sse/streamChat'
import type { ChatMessage, Conversation } from '../types'

let localIdSeq = 0
function nextLocalId(): string {
  localIdSeq += 1
  return `local-${Date.now()}-${localIdSeq}`
}

function byUpdatedAtDesc(a: Conversation, b: Conversation): number {
  return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
}

interface ChatState {
  conversations: Conversation[]
  conversationsLoading: boolean
  activeConversationId: string | null
  messages: ChatMessage[]
  messagesLoading: boolean
  messagesError: string | null
  streaming: boolean
  abortController: AbortController | null

  loadConversations: () => Promise<void>
  /** 切换当前会话；null 表示「新对话」空态 */
  openConversation: (id: string | null) => Promise<void>
  /** 发送消息并消费 SSE 流；新会话创建成功时通过回调通知（用于路由跳转） */
  sendMessage: (content: string, onConversationCreated?: (id: string) => void) => Promise<void>
  stopStreaming: () => void
  renameConversation: (id: string, title: string) => Promise<void>
  /** 返回被删除的是否为当前会话（调用方据此决定是否跳回首页） */
  removeConversation: (id: string) => Promise<boolean>
  /** 退出登录时清空全部会话状态 */
  reset: () => void
}

export const useChatStore = create<ChatState>((set, get) => ({
  conversations: [],
  conversationsLoading: false,
  activeConversationId: null,
  messages: [],
  messagesLoading: false,
  messagesError: null,
  streaming: false,
  abortController: null,

  async loadConversations() {
    set({ conversationsLoading: true })
    try {
      const conversations = await chatApi.listConversations()
      set({ conversations: [...conversations].sort(byUpdatedAtDesc), conversationsLoading: false })
    } catch {
      set({ conversationsLoading: false })
    }
  },

  async openConversation(id) {
    const { activeConversationId, streaming, abortController } = get()
    if (id === activeConversationId) return
    // 切走时终止未完成的流
    if (streaming) abortController?.abort()

    if (id === null) {
      set({
        activeConversationId: null,
        messages: [],
        messagesLoading: false,
        messagesError: null,
        streaming: false,
        abortController: null,
      })
      return
    }

    set({
      activeConversationId: id,
      messages: [],
      messagesLoading: true,
      messagesError: null,
      streaming: false,
      abortController: null,
    })
    try {
      const messages = await chatApi.listMessages(id)
      if (get().activeConversationId === id) {
        set({ messages, messagesLoading: false })
      }
    } catch (error) {
      if (get().activeConversationId === id) {
        set({ messagesLoading: false, messagesError: extractErrorMessage(error, '加载消息失败') })
      }
    }
  },

  async sendMessage(content, onConversationCreated) {
    const trimmed = content.trim()
    if (!trimmed || get().streaming) return

    const conversationId = get().activeConversationId ?? undefined
    const userMessage: ChatMessage = {
      id: nextLocalId(),
      role: 'USER',
      content: trimmed,
      createdAt: new Date().toISOString(),
    }
    // 闭包内可变：meta 帧到达后替换为服务端 messageId
    let assistantId = nextLocalId()
    const assistantMessage: ChatMessage = {
      id: assistantId,
      role: 'ASSISTANT',
      content: '',
      reasoningContent: '',
      toolCalls: [],
      ragSources: null,
      streaming: true,
    }

    const controller = new AbortController()
    set((state) => ({
      messages: [...state.messages, userMessage, assistantMessage],
      streaming: true,
      abortController: controller,
      messagesError: null,
    }))

    const patchAssistant = (patch: (message: ChatMessage) => ChatMessage) => {
      set((state) => ({
        messages: state.messages.map((m) => (m.id === assistantId ? patch(m) : m)),
      }))
    }

    const handleEvent = (event: SseEvent) => {
      switch (event.type) {
        case 'meta': {
          if (!get().activeConversationId) {
            set({ activeConversationId: event.conversationId })
            onConversationCreated?.(event.conversationId)
            void get().loadConversations()
          }
          if (event.messageId && event.messageId !== assistantId) {
            const previousId = assistantId
            assistantId = event.messageId
            set((state) => ({
              messages: state.messages.map((m) =>
                m.id === previousId ? { ...m, id: event.messageId } : m,
              ),
            }))
          }
          break
        }
        case 'text-delta':
          patchAssistant((m) => ({ ...m, content: m.content + event.delta }))
          break
        case 'reasoning':
          patchAssistant((m) => ({
            ...m,
            reasoningContent: (m.reasoningContent ?? '') + event.delta,
          }))
          break
        case 'tool-call':
          patchAssistant((m) => ({
            ...m,
            toolCalls: [
              ...(m.toolCalls ?? []),
              { id: event.id, name: event.name, args: event.args, done: false },
            ],
          }))
          break
        case 'tool-result':
          patchAssistant((m) => ({
            ...m,
            toolCalls: (m.toolCalls ?? []).map((call) =>
              call.id === event.id ? { ...call, result: event.result, done: true } : call,
            ),
          }))
          break
        case 'rag-source':
          patchAssistant((m) => ({
            ...m,
            ragSources: [...(m.ragSources ?? []), ...event.sources],
          }))
          break
        case 'done':
          patchAssistant((m) => ({ ...m, tokenUsage: event.usage ?? null, streaming: false }))
          break
        case 'error':
          patchAssistant((m) => ({
            ...m,
            error: { code: event.code, message: event.message },
            streaming: false,
          }))
          break
      }
    }

    try {
      await streamChat({
        conversationId,
        content: trimmed,
        signal: controller.signal,
        onEvent: handleEvent,
      })
    } catch (error) {
      if (!controller.signal.aborted) {
        patchAssistant((m) => ({
          ...m,
          error: m.error ?? { message: extractErrorMessage(error, '生成中断，请重试') },
          streaming: false,
        }))
      }
    } finally {
      patchAssistant((m) => ({ ...m, streaming: false }))
      set((state) =>
        state.abortController === controller ? { streaming: false, abortController: null } : {},
      )
      // 刷新列表：拿到自动生成的标题 / 最新 updatedAt 排序
      void get().loadConversations()
    }
  },

  stopStreaming() {
    get().abortController?.abort()
  },

  async renameConversation(id, title) {
    const updated = await chatApi.renameConversation(id, title)
    set((state) => ({
      conversations: state.conversations.map((c) => (c.id === id ? { ...c, ...updated } : c)),
    }))
  },

  async removeConversation(id) {
    await chatApi.deleteConversation(id)
    const wasActive = get().activeConversationId === id
    set((state) => ({
      conversations: state.conversations.filter((c) => c.id !== id),
      ...(wasActive
        ? { activeConversationId: null, messages: [], messagesError: null }
        : {}),
    }))
    return wasActive
  },

  reset() {
    get().abortController?.abort()
    set({
      conversations: [],
      conversationsLoading: false,
      activeConversationId: null,
      messages: [],
      messagesLoading: false,
      messagesError: null,
      streaming: false,
      abortController: null,
    })
  },
}))

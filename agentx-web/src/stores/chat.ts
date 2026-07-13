import { create } from 'zustand'
import * as chatApi from '../api/chat'
import * as codingApi from '../api/coding'
import { extractErrorMessage } from '../api/http'
import type { SseEvent } from '../sse/events'
import { streamChat } from '../sse/streamChat'
import type { ApprovalItem, ChatMessage, CodingMode, Conversation, Workspace } from '../types'

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

  /** CodeAgent 会话设置（输入框工具条驱动）。workspaceId 非空即进入 coding 模式。 */
  modelConfigId: string | null
  workspaceId: string | null
  codingMode: CodingMode
  /** 本次检索追加的知识库（输入框多选，独立于项目） */
  kbIds: string[]
  setModelConfigId: (id: string | null) => void
  setWorkspaceId: (id: string | null) => void
  setCodingMode: (mode: CodingMode) => void
  setKbIds: (ids: string[]) => void

  /** 项目列表单一数据源：Sidebar/ProjectPicker/管理页共享，创建/编辑/删除后 refresh */
  projects: Workspace[]
  loadProjects: () => Promise<void>

  loadConversations: () => Promise<void>
  /** 切换当前会话；null 表示「新对话」空态 */
  openConversation: (id: string | null) => Promise<void>
  /** 发送消息并消费 SSE 流；新会话创建成功时通过回调通知（用于路由跳转） */
  sendMessage: (content: string, onConversationCreated?: (id: string) => void) => Promise<void>
  stopStreaming: () => void
  /** Ask 审批回传：批准/拒绝一个待审批操作，乐观更新对应卡片状态 */
  resolveApproval: (approvalId: string, approved: boolean) => Promise<void>
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

  modelConfigId: null,
  workspaceId: null,
  codingMode: 'ASK',
  kbIds: [],
  setModelConfigId: (id) => set({ modelConfigId: id }),
  setWorkspaceId: (id) => set({ workspaceId: id }),
  setCodingMode: (mode) => set({ codingMode: mode }),
  setKbIds: (ids) => set({ kbIds: ids }),

  projects: [],
  async loadProjects() {
    try {
      set({ projects: await codingApi.listWorkspaces() })
    } catch {
      /* 未登录/后端不可用时保持现状 */
    }
  },

  async loadConversations() {
    set({ conversationsLoading: true })
    try {
      const conversations = await chatApi.listConversations()
      set({ conversations: [...conversations].sort(byUpdatedAtDesc), conversationsLoading: false })
      // 深链打开 /c/:id 时列表晚到：就位后补同步当前会话的项目归属
      const active = get().activeConversationId
      if (active) {
        const conv = conversations.find((c) => c.id === active)
        if (conv) set({ workspaceId: conv.workspaceId ?? null })
      }
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
      // 同步会话的项目归属：编码会话续聊仍走 coding 模式
      workspaceId: get().conversations.find((c) => c.id === id)?.workspaceId ?? null,
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
        case 'approval-request': {
          const item: ApprovalItem = {
            approvalId: event.approvalId,
            toolName: event.toolName,
            kind: event.kind,
            preview: event.preview,
            status: 'pending',
          }
          patchAssistant((m) => ({ ...m, approvals: [...(m.approvals ?? []), item] }))
          break
        }
        case 'done':
          patchAssistant((m) => ({ ...m, tokenUsage: event.usage ?? null, streaming: false }))
          // done 帧即协议层终止信号：立即复原发送按钮并主动断开，
          // 不依赖服务端关流姿势（chunked 未终结时浏览器可能挂住 reader）
          set((state) =>
            state.abortController === controller
              ? { streaming: false, abortController: null }
              : {},
          )
          controller.abort()
          break
        case 'error':
          patchAssistant((m) => ({
            ...m,
            error: { code: event.code, message: event.message },
            streaming: false,
          }))
          // error 帧同为终止信号（业务错误不断流的设计），立即复原并断开
          set((state) =>
            state.abortController === controller
              ? { streaming: false, abortController: null }
              : {},
          )
          controller.abort()
          break
      }
    }

    const { modelConfigId, workspaceId, codingMode, kbIds } = get()
    try {
      await streamChat({
        conversationId,
        content: trimmed,
        ...(modelConfigId ? { modelConfigId } : {}),
        // workspaceId 非空才进入 coding 模式；mode 仅在 coding 时有意义
        ...(workspaceId ? { workspaceId, mode: codingMode } : {}),
        ...(kbIds.length > 0 ? { kbIds } : {}),
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

  async resolveApproval(approvalId, approved) {
    const nextStatus = approved ? 'approved' : 'rejected'
    const patchStatus = (status: ApprovalItem['status']) =>
      set((state) => ({
        messages: state.messages.map((m) =>
          m.approvals?.some((a) => a.approvalId === approvalId)
            ? {
                ...m,
                approvals: m.approvals.map((a) =>
                  a.approvalId === approvalId ? { ...a, status } : a,
                ),
              }
            : m,
        ),
      }))
    // 乐观置终态，失败回滚为 pending
    patchStatus(nextStatus)
    try {
      await codingApi.resolveApproval(approvalId, approved)
    } catch (error) {
      patchStatus('pending')
      throw error
    }
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
      modelConfigId: null,
      workspaceId: null,
      codingMode: 'ASK',
      kbIds: [],
    })
  },
}))

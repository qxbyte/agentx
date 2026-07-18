import { create } from 'zustand'
import * as chatApi from '../api/chat'
import * as codingApi from '../api/coding'
import { extractErrorMessage, isNotFoundError } from '../api/http'
import type { SseEvent } from '../sse/events'
import { streamChat } from '../sse/streamChat'
import { uploadAttachments } from '../api/attachments'
import { listSkillMenu } from '../api/skills'
import type { AttachmentEntry } from '../lib/attachments'
import { isImage, MAX_ATTACHMENT_FILES, preprocessImage } from '../lib/attachments'
import type {
  ApprovalItem,
  AttachmentMeta,
  ChatMessage,
  CodingMode,
  Conversation,
  PendingAttachment,
  PlanState,
  PlanStep,
  QuestionAnswer,
  QuestionItem,
  SkillMeta,
  Workspace,
} from '../types'

let localIdSeq = 0
function nextLocalId(): string {
  localIdSeq += 1
  return `local-${Date.now()}-${localIdSeq}`
}

/** 审批回传在途集合：同步防重入（busy state 是异步的，同一帧连点会发两个请求） */
const approvalInFlight = new Set<string>()

/* 编码模式按会话记忆（localStorage）：模式是会话属性而非全局偏好——
   否则一个会话切到 Bypass 会泄漏到所有会话。新会话默认 ASK（最安全）。 */
const MODES_KEY = 'agentx.conversationModes'

function loadModeMap(): Record<string, CodingMode> {
  try {
    return JSON.parse(localStorage.getItem(MODES_KEY) ?? '{}') as Record<string, CodingMode>
  } catch {
    return {}
  }
}

function rememberMode(conversationId: string, mode: CodingMode) {
  const map = loadModeMap()
  map[conversationId] = mode
  try {
    localStorage.setItem(MODES_KEY, JSON.stringify(map))
  } catch {
    /* 存储不可用时静默：仅影响刷新后的模式回填 */
  }
}

function forgetMode(conversationId: string) {
  const map = loadModeMap()
  if (!(conversationId in map)) return
  delete map[conversationId]
  try {
    localStorage.setItem(MODES_KEY, JSON.stringify(map))
  } catch {
    /* 同上 */
  }
}

function modeOf(conversationId: string): CodingMode {
  return loadModeMap()[conversationId] ?? 'ASK'
}

/**
 * 后台流式会话：切走会话不再断流——SSE 连接与实时快照由此持有，按会话隔离。
 * 切回时在服务端历史上叠加在途的助手消息快照（该轮用户提问开流即落库，无需叠加）。
 * done=true 表示流已终结但用户尚未切回（快照留作过渡，切回叠加后清理）。
 */
interface StreamSession {
  /** 新会话在 meta 帧到达前未知 */
  conversationId: string | null
  controller: AbortController
  /** 实时构建中的助手消息快照（与激活视图的 messages 条目同步演进） */
  assistant: ChatMessage
  done: boolean
}
const liveStreams = new Map<string, StreamSession>()
/** 新会话 meta 帧前的过渡持有（同一时刻至多一个新会话在开流） */
let pendingNewSession: StreamSession | null = null

/** 把某条审批置为指定终态；stillPendingOnly 时仅当当前是 pending 才翻转（权威帧优先） */
function patchApprovalIn(
  messages: ChatMessage[],
  approvalId: string,
  status: ApprovalItem['status'],
  stillPendingOnly = false,
): ChatMessage[] {
  return messages.map((m) =>
    m.approvals?.some((a) => a.approvalId === approvalId)
      ? {
          ...m,
          approvals: m.approvals.map((a) =>
            a.approvalId === approvalId && (!stillPendingOnly || a.status === 'pending')
              ? { ...a, status }
              : a,
          ),
        }
      : m,
  )
}

/** 提问回传在途集合：同步防重入（与审批同理） */
const questionInFlight = new Set<string>()

/** 把某条提问置为指定终态；权威帧优先语义同审批 */
function patchQuestionIn(
  messages: ChatMessage[],
  questionId: string,
  status: QuestionItem['status'],
  answers?: QuestionAnswer[] | null,
): ChatMessage[] {
  return messages.map((m) =>
    m.questions?.some((q) => q.questionId === questionId)
      ? {
          ...m,
          questions: m.questions.map((q) =>
            q.questionId === questionId
              ? { ...q, status, ...(answers !== undefined ? { answers } : {}) }
              : q,
          ),
        }
      : m,
  )
}

/** 释放图片附件的本地预览 blob URL（移除/发送/切会话时调用，防内存泄漏） */
function releasePreviews(list: PendingAttachment[]) {
  for (const a of list) {
    if (a.previewUrl) URL.revokeObjectURL(a.previewUrl)
  }
}

function byUpdatedAtDesc(a: Conversation, b: Conversation): number {
  return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
}

/** 后端历史消息的 jsonb 字段原样返回字符串，渲染前解析为对象（坏数据回退 null 不崩页） */
function parseJsonField<T>(v: unknown): T | null {
  if (v == null) return null
  if (typeof v === 'string') {
    try {
      return JSON.parse(v) as T
    } catch {
      return null
    }
  }
  return v as T
}

const PLAN_STATUSES: ReadonlySet<string> = new Set(['pending', 'in_progress', 'completed'])

/** 解析 updatePlan 的参数（SSE 帧 args 为 JSON 字符串 / 历史恢复为 jsonb 原文）；
    非法结构或空步骤返回 null，非法状态的步骤被丢弃（模型偶发脏数据不崩面板） */
function parsePlanState(raw: unknown): PlanState | null {
  const parsed = typeof raw === 'string' ? parseJsonField<PlanState>(raw) : (raw as PlanState | null)
  if (!parsed || !Array.isArray(parsed.steps)) return null
  const steps = parsed.steps.filter(
    (s): s is PlanStep =>
      !!s && typeof s.step === 'string' && s.step !== '' && PLAN_STATUSES.has(s.status),
  )
  const title = typeof parsed.title === 'string' && parsed.title.trim() !== '' ? parsed.title.trim() : null
  return steps.length > 0 ? { title, steps, explanation: parsed.explanation ?? null } : null
}

function normalizeMessage(m: ChatMessage): ChatMessage {
  return {
    ...m,
    toolCalls: parseJsonField(m.toolCalls),
    ragSources: parseJsonField(m.ragSources),
    tokenUsage: parseJsonField(m.tokenUsage),
    attachments: parseJsonField(m.attachments),
  }
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

  /** 输入框待发送附件（上传即解析，随下一条消息发送后清空） */
  attachments: PendingAttachment[]
  /** 追加附件并立即上传解析；超出单条消息上限的部分丢弃并由调用方提示 */
  addAttachments: (entries: AttachmentEntry[]) => Promise<void>
  removeAttachment: (key: string) => void

  /** 当前会话的任务计划（updatePlan 工具驱动，固定输入框上方展示）；null 无计划 */
  plan: PlanState | null
  /** 用户手动关闭过面板：阻止 loadConversations 回填复活已关闭的计划 */
  planDismissed: boolean
  clearPlan: () => void

  /** CodeAgent 会话设置（输入框工具条驱动）。workspaceId 非空即进入 coding 模式。 */
  modelConfigId: string | null
  workspaceId: string | null
  codingMode: CodingMode
  /** 本次检索追加的知识库（输入框多选，独立于项目） */
  kbIds: string[]
  /** 用户在当前会话中手动切换过模型：阻止列表刷新把选择重置回会话固化值 */
  modelChoiceTouched: boolean
  setModelConfigId: (id: string | null) => void
  setWorkspaceId: (id: string | null) => void
  setCodingMode: (mode: CodingMode) => void
  setKbIds: (ids: string[]) => void

  /** 从项目入口新建的对话：项目归属与知识库锁定沿用项目，不允许再选 */
  projectLocked: boolean
  setProjectLocked: (locked: boolean) => void

  /** 项目列表单一数据源：Sidebar/ProjectPicker/管理页共享，创建/编辑/删除后 refresh */
  projects: Workspace[]
  loadProjects: () => Promise<void>

  /** Skill 斜杠命令：/ 补全菜单元数据（进入聊天页加载，管理页变更后刷新） */
  skills: SkillMeta[]
  loadSkills: () => Promise<void>

  loadConversations: () => Promise<void>
  /** 切换当前会话；null 表示「新对话」空态 */
  openConversation: (id: string | null) => Promise<void>
  /** 发送消息并消费 SSE 流；新会话创建成功时通过回调通知（用于路由跳转） */
  sendMessage: (content: string, onConversationCreated?: (id: string) => void) => Promise<void>
  stopStreaming: () => void
  /** 重发一轮失败的对话（仅限未落库的本地轮次，如登录过期 401）：
      移除失败的助手气泡与对应用户提问，原文重新发送 */
  resendFailed: (assistantMessageId: string) => void
  /** Ask 审批回传：批准/拒绝一个待审批操作，乐观更新对应卡片状态 */
  resolveApproval: (approvalId: string, approved: boolean) => Promise<void>
  /** askUserQuestion 答案回传：提交后卡片乐观置终态，权威终态由 question-result 帧下发 */
  resolveQuestion: (questionId: string, answers: QuestionAnswer[]) => Promise<void>
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

  attachments: [],
  async addAttachments(entries) {
    const room = MAX_ATTACHMENT_FILES - get().attachments.length
    if (room <= 0) return
    // 图片上传前预处理：1568px 降采样 + EXIF 方向烘焙（GIF 原样保留）
    const accepted = await Promise.all(
      entries.slice(0, room).map(async (e) =>
        isImage(e.file.name) ? { ...e, file: await preprocessImage(e.file) } : e,
      ),
    )
    const pending: PendingAttachment[] = accepted.map((e) => ({
      key: nextLocalId(),
      filename: e.file.name,
      ...(e.relPath ? { relPath: e.relPath } : {}),
      kind: isImage(e.file.name) ? ('image' as const) : ('text' as const),
      ...(isImage(e.file.name) ? { previewUrl: URL.createObjectURL(e.file) } : {}),
      sizeBytes: e.file.size,
      status: 'uploading' as const,
    }))
    set((state) => ({ attachments: [...state.attachments, ...pending] }))
    try {
      const results = await uploadAttachments(accepted)
      set((state) => ({
        attachments: state.attachments.map((a) => {
          const idx = pending.findIndex((p) => p.key === a.key)
          if (idx < 0) return a
          const r = results[idx]
          if (!r || r.error || !r.id) {
            return { ...a, status: 'failed' as const, error: r?.error ?? '上传失败' }
          }
          return { ...a, id: r.id, truncated: r.truncated, status: 'ready' as const }
        }),
      }))
    } catch (error) {
      const message = extractErrorMessage(error, '上传失败')
      set((state) => ({
        attachments: state.attachments.map((a) =>
          pending.some((p) => p.key === a.key) ? { ...a, status: 'failed' as const, error: message } : a,
        ),
      }))
    }
  },
  removeAttachment(key) {
    set((state) => {
      releasePreviews(state.attachments.filter((a) => a.key === key))
      return { attachments: state.attachments.filter((a) => a.key !== key) }
    })
  },

  plan: null,
  planDismissed: false,
  clearPlan: () => set({ plan: null, planDismissed: true }),

  modelConfigId: null,
  modelChoiceTouched: false,
  workspaceId: null,
  codingMode: 'ASK',
  kbIds: [],
  setModelConfigId: (id) => set({ modelConfigId: id, modelChoiceTouched: true }),
  setWorkspaceId: (id) => set({ workspaceId: id }),
  setCodingMode: (mode) => {
    set({ codingMode: mode })
    const { activeConversationId, workspaceId } = get()
    // 模式是会话属性:立即写入按会话记忆,切会话/刷新后各自回填,互不串扰
    if (activeConversationId) {
      rememberMode(activeConversationId, mode)
    }
    // 编码会话内切模式立即回传后端：在跑轮次按新模式放行/拦截，
    // 切 AUTO 时未决审批被一次性批准（卡片经 approval-result 帧翻转）。
    // 失败静默：下一轮请求仍会携带最新模式，不影响最终一致
    if (activeConversationId && workspaceId) {
      void codingApi.updateCodingMode(activeConversationId, mode).catch(() => {})
    }
  },
  setKbIds: (ids) => set({ kbIds: ids }),

  projectLocked: false,
  setProjectLocked: (locked) => set({ projectLocked: locked }),

  projects: [],
  async loadProjects() {
    try {
      set({ projects: await codingApi.listWorkspaces() })
    } catch {
      /* 未登录/后端不可用时保持现状 */
    }
  },

  skills: [],
  async loadSkills() {
    try {
      set({ skills: await listSkillMenu() })
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
        if (conv) {
          // 用户手动切换过模型 → 不回填，避免把新选择重置回会话固化值（严重 bug 修复）
          set({
            workspaceId: conv.workspaceId ?? null,
            ...(get().modelChoiceTouched ? {} : { modelConfigId: conv.modelConfigId ?? null }),
          })
          // 深链打开时补回填持久化的计划（流式中的实时计划 / 已手动关闭的不覆盖）
          if (get().plan === null && !get().planDismissed) {
            const plan = parsePlanState(conv.planState)
            if (plan) set({ plan })
          } else if (get().plan !== null && !conv.planState) {
            // 反向回填：新会话流式中计划先于列表就位（拉到的快照还没有 plan_state），
            // 把实时计划写回条目，保证切走再切回可恢复
            const live = get().plan
            set((state) => ({
              conversations: state.conversations.map((c) =>
                c.id === active ? { ...c, planState: JSON.stringify(live) } : c,
              ),
            }))
          }
        }
      }
    } catch {
      set({ conversationsLoading: false })
    }
  },

  async openConversation(id) {
    const { activeConversationId } = get()
    if (id === activeConversationId) return
    // 切走不再断流：进行中的任务转后台继续（liveStreams 持有 SSE 连接与快照）

    if (id === null) {
      set({
        activeConversationId: null,
        messages: [],
        messagesLoading: false,
        messagesError: null,
        streaming: false,
        abortController: null,
        plan: null,
        planDismissed: false,
        attachments: [],
        modelConfigId: null,
        modelChoiceTouched: false,
        // 新对话回到最安全的默认模式,不继承上一个会话的选择
        codingMode: 'ASK',
      })
      return
    }

    const conv = get().conversations.find((c) => c.id === id)
    const session = liveStreams.get(id)
    const live = !!session && !session.done
    set({
      activeConversationId: id,
      messages: [],
      messagesLoading: true,
      messagesError: null,
      // 切回流式中的会话：恢复生成中状态与停止按钮
      streaming: live,
      abortController: live ? session.controller : null,
      // 同步会话的项目归属：编码会话续聊仍走 coding 模式
      workspaceId: conv?.workspaceId ?? null,
      // 会话记住上次选择的模型：重开/刷新后选择器回填；切会话即清除手动切换标记
      modelConfigId: conv?.modelConfigId ?? null,
      modelChoiceTouched: false,
      // 恢复该会话自己的编码模式(按会话记忆,未记录则回默认 ASK)
      codingMode: modeOf(id),
      // 恢复会话持久化的计划面板（列表未就位时由 loadConversations 补回填）
      plan: parsePlanState(conv?.planState),
      planDismissed: false,
      attachments: [],
    })
    try {
      const fetched = (await chatApi.listMessages(id)).map(normalizeMessage)
      if (get().activeConversationId === id) {
        let messages = fetched
        // 持有切换时捕获的引用：fetch 在途中流恰好终结会把 session 移出表，
        // 但落库可能晚于本次快照——引用仍在，叠加最终内容不丢消息
        const s = session ?? liveStreams.get(id)
        if (s) {
          // 该轮用户提问开流即落库（已在服务端列表里）；助手消息完成才落库，
          // 未落库时叠加实时快照，后续 SSE 帧继续增量更新这条消息
          if (!fetched.some((m) => m.id === s.assistant.id)) {
            messages = [...fetched, s.assistant]
          }
          // 后台期间已终结的流：快照叠加完成即清理（服务端数据已是权威）
          if (s.done) liveStreams.delete(id)
        }
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
    if (!trimmed) return

    const conversationId = get().activeConversationId ?? undefined
    // 按会话防重入：当前会话已有在途流则拦截；其他会话的后台流不影响本会话发送
    if (conversationId ? liveStreams.has(conversationId) : pendingNewSession !== null) return
    if (get().streaming) return
    // 附件随消息发出：只带 ready 项（uploading/failed 由输入框侧拦截或用户自行移除）
    const readyAttachments = get().attachments.filter((a) => a.status === 'ready' && a.id)
    const attachmentIds = readyAttachments.map((a) => a.id as string)
    const attachmentMetas: AttachmentMeta[] = readyAttachments.map((a) => ({
      id: a.id as string,
      filename: a.filename,
      kind: a.kind,
      sizeBytes: a.sizeBytes,
    }))
    const userMessage: ChatMessage = {
      id: nextLocalId(),
      role: 'USER',
      content: trimmed,
      ...(attachmentMetas.length > 0 ? { attachments: attachmentMetas } : {}),
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
    // 注册后台流式会话：切走后连接与快照都由 session 持有，切回可无缝恢复
    const session: StreamSession = {
      conversationId: conversationId ?? null,
      controller,
      assistant: assistantMessage,
      done: false,
    }
    if (conversationId) liveStreams.set(conversationId, session)
    else pendingNewSession = session

    set((state) => {
      releasePreviews(state.attachments)
      return {
        messages: [...state.messages, userMessage, assistantMessage],
        streaming: true,
        abortController: controller,
        messagesError: null,
        attachments: [],
      }
    })

    /** 本流所属会话当前是否在看（新会话 meta 前双方都是 null 也算在看） */
    const isActiveView = () => session.conversationId === (get().activeConversationId ?? null)

    // 双写：session 快照始终最新（后台也在演进）；激活视图时同步 patch 消息列表
    const patchAssistant = (patch: (message: ChatMessage) => ChatMessage) => {
      session.assistant = patch(session.assistant)
      if (isActiveView()) {
        set((state) => ({
          messages: state.messages.map((m) => (m.id === assistantId ? patch(m) : m)),
        }))
      }
    }

    const handleEvent = (event: SseEvent) => {
      switch (event.type) {
        case 'meta': {
          if (session.conversationId === null) {
            // 新会话就位：注册到按会话索引的后台流表
            session.conversationId = event.conversationId
            liveStreams.set(event.conversationId, session)
            // 首条消息携带的模式固化为该会话的模式记忆
            rememberMode(event.conversationId, codingMode)
            if (pendingNewSession === session) pendingNewSession = null
            // 仅当用户仍停留在新对话视图才接管路由（已切走则任务静默后台跑）
            if (!get().activeConversationId) {
              set({ activeConversationId: event.conversationId })
              onConversationCreated?.(event.conversationId)
            }
            void get().loadConversations()
          }
          if (event.messageId && event.messageId !== assistantId) {
            const previousId = assistantId
            assistantId = event.messageId
            session.assistant = { ...session.assistant, id: event.messageId }
            if (isActiveView()) {
              set((state) => ({
                messages: state.messages.map((m) =>
                  m.id === previousId ? { ...m, id: event.messageId } : m,
                ),
              }))
            }
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
        case 'tool-call': {
          // 计划更新走独立面板（输入框上方），不进消息内工具卡
          if (event.name === 'updatePlan') {
            const plan = parsePlanState(event.args)
            if (plan) {
              // 按本流所属会话路由：面板仅在看该会话时更新；列表条目始终回写
              // （openConversation 恢复计划读的是列表快照，不回写切回就丢）
              const convId = session.conversationId
              set((state) => ({
                ...(isActiveView() ? { plan, planDismissed: false } : {}),
                conversations: state.conversations.map((c) =>
                  c.id === convId && typeof event.args === 'string'
                    ? { ...c, planState: event.args }
                    : c,
                ),
              }))
            }
            break
          }
          patchAssistant((m) => ({
            ...m,
            toolCalls: [
              ...(m.toolCalls ?? []),
              { id: event.id, name: event.name, args: event.args, done: false },
            ],
          }))
          break
        }
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
        case 'approval-result':
          // 权威终态帧：后端审批 future 落定后下发（批准/拒绝/超时），
          // 卡片终态以此为准——覆盖乐观更新丢失/重复点击等各种失同步
          session.assistant =
            patchApprovalIn([session.assistant], event.approvalId, event.outcome)[0] ??
            session.assistant
          if (isActiveView()) {
            set((state) => ({
              messages: patchApprovalIn(state.messages, event.approvalId, event.outcome),
            }))
          }
          break
        case 'question-request': {
          const item: QuestionItem = {
            questionId: event.questionId,
            questions: event.questions,
            status: 'pending',
          }
          patchAssistant((m) => ({ ...m, questions: [...(m.questions ?? []), item] }))
          break
        }
        case 'question-result': {
          // 权威终态帧（answered/expired）：语义同审批终态
          const answers = event.answers ? parseJsonField<QuestionAnswer[]>(event.answers) : undefined
          session.assistant =
            patchQuestionIn([session.assistant], event.questionId, event.outcome, answers)[0] ??
            session.assistant
          if (isActiveView()) {
            set((state) => ({
              messages: patchQuestionIn(state.messages, event.questionId, event.outcome, answers),
            }))
          }
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

    const { modelConfigId, modelChoiceTouched, workspaceId, codingMode, kbIds } = get()
    try {
      await streamChat({
        conversationId,
        content: trimmed,
        ...(modelConfigId ? { modelConfigId } : {}),
        // 用户显式切回「默认模型」：告知后端清除会话固化的模型选择
        ...(modelConfigId === null && modelChoiceTouched ? { useDefaultModel: true } : {}),
        // workspaceId 非空才进入 coding 模式
        ...(workspaceId ? { workspaceId } : {}),
        // 模式对普通对话同样生效(本地工具的 Plan/Ask/Auto)
        mode: codingMode,
        // 知识库是会话创建期属性：仅新会话首条消息携带，随会话固化；续聊后端一律沿用
        ...(!conversationId && kbIds.length > 0 ? { kbIds } : {}),
        ...(attachmentIds.length > 0 ? { attachmentIds } : {}),
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
      // 流已终止：仍 pending 的审批卡一律置失效（后端断流时会取消未决审批，
      // 注册项已不存在，留着可点的按钮只会 404）
      patchAssistant((m) => ({
        ...m,
        streaming: false,
        approvals:
          m.approvals?.map((a) => (a.status === 'pending' ? { ...a, status: 'expired' } : a)) ??
          m.approvals,
      }))
      session.done = true
      if (pendingNewSession === session) pendingNewSession = null
      // 在看本会话：视图已是最新，session 使命完成即清理；
      // 不在看：保留 done 快照供切回叠加（openConversation 叠加后清理）
      if (session.conversationId && isActiveView()) {
        liveStreams.delete(session.conversationId)
      }
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

  resendFailed(assistantMessageId) {
    const { messages, streaming } = get()
    if (streaming) return
    const idx = messages.findIndex((m) => m.id === assistantMessageId)
    if (idx < 0) return
    const userMessage = messages
      .slice(0, idx)
      .reverse()
      .find((m) => m.role === 'USER')
    if (!userMessage) return
    set({
      messages: messages.filter((m) => m.id !== assistantMessageId && m.id !== userMessage.id),
    })
    void get().sendMessage(userMessage.content)
  },

  async resolveQuestion(questionId, answers) {
    // 同步防重入：与审批同理，同一帧连点会发两个请求
    if (questionInFlight.has(questionId)) return
    questionInFlight.add(questionId)
    const patchStatus = (status: QuestionItem['status']) =>
      set((state) => ({ messages: patchQuestionIn(state.messages, questionId, status, answers) }))
    // 乐观置终态；真正的权威终态由 question-result 帧下发（见 handleEvent）
    patchStatus('answered')
    try {
      await chatApi.answerQuestion(questionId, answers)
    } catch (error) {
      // 404 = 后端已无此项（超时/会话结束）→ 置失效；其余错误回滚 pending 允许重试
      patchStatus(isNotFoundError(error) ? 'expired' : 'pending')
      throw error
    } finally {
      questionInFlight.delete(questionId)
    }
  },

  async resolveApproval(approvalId, approved) {
    // 同步防重入：busy state 异步生效，同一帧内连点会发两个请求——
    // 第二个必 404（首个已 remove 注册项）并把已成功的卡片误回滚
    if (approvalInFlight.has(approvalId)) return
    approvalInFlight.add(approvalId)
    const patchStatus = (status: ApprovalItem['status']) =>
      set((state) => ({ messages: patchApprovalIn(state.messages, approvalId, status) }))
    // 乐观置终态；真正的权威终态由 approval-result 帧下发（见 handleEvent）
    patchStatus(approved ? 'approved' : 'rejected')
    try {
      await codingApi.resolveApproval(approvalId, approved)
    } catch (error) {
      // 404 = 后端已无此项（已处理/超时/会话结束）→ 置失效；
      // 其余错误（网络抖动等）才回滚 pending 允许重试
      patchStatus(isNotFoundError(error) ? 'expired' : 'pending')
      throw error
    } finally {
      approvalInFlight.delete(approvalId)
    }
  },

  async renameConversation(id, title) {
    const updated = await chatApi.renameConversation(id, title)
    set((state) => ({
      conversations: state.conversations.map((c) => (c.id === id ? { ...c, ...updated } : c)),
    }))
  },

  async removeConversation(id) {
    // 删除会话连带终止其后台流
    const session = liveStreams.get(id)
    if (session) {
      session.controller.abort()
      liveStreams.delete(id)
    }
    await chatApi.deleteConversation(id)
    forgetMode(id)
    const wasActive = get().activeConversationId === id
    set((state) => ({
      conversations: state.conversations.filter((c) => c.id !== id),
      ...(wasActive
        ? { activeConversationId: null, messages: [], messagesError: null, plan: null, planDismissed: false }
        : {}),
    }))
    return wasActive
  },

  reset() {
    // 退出登录：终止全部后台流（含未就位的新会话流）
    liveStreams.forEach((s) => s.controller.abort())
    liveStreams.clear()
    pendingNewSession?.controller.abort()
    pendingNewSession = null
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
      plan: null,
      planDismissed: false,
      attachments: [],
      modelConfigId: null,
      modelChoiceTouched: false,
      workspaceId: null,
      codingMode: 'ASK',
      kbIds: [],
      projectLocked: false,
    })
  },
}))

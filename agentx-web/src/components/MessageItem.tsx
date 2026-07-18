import { RotateCcw, Zap } from 'lucide-react'
import { memo } from 'react'
import type { ReactNode } from 'react'
import { useChatStore } from '../stores/chat'
import type {
  ChatMessage,
  QuestionAnswer,
  QuestionItem,
  QuestionSpec,
  ToolCallInfo,
} from '../types'
import ActivityIndicator, { activityLabel } from './ActivityIndicator'
import AttachmentCard from './AttachmentCard'
import AttachmentThumb from './AttachmentThumb'
import ApprovalCard from './coding/ApprovalCard'
import FileCard from './FileCard'
import QuestionCard from './QuestionCard'
import Logo from './Logo'
import ToolCallGroup from './ToolCallGroup'
import MarkdownRenderer from './MarkdownRenderer'
import ReasoningBlock from './ReasoningBlock'
import { SourceList } from './SourceBadge'
import ToolCallCard from './ToolCallCard'

interface MessageItemProps {
  message: ChatMessage
}

function MessageItem({ message }: MessageItemProps) {
  // hook 须在 role 分支前调用；skills 极少变化，订阅代价可忽略
  const skills = useChatStore((s) => s.skills)

  if (message.role === 'USER') {
    // /name 开头且命中已知 skill → 渲染命令徽章（对标 Claude Code 的命令展示）
    const command = /^\/([a-z0-9-]+(?::[a-z0-9-]+)?)(?:\s+([\s\S]*))?$/.exec(message.content.trim())
    const skillName = command && skills.some((s) => s.name === command[1]) ? command[1] : null
    return (
      <div className="ax-message ax-message--user">
        <div className="ax-user-stack">
          {message.attachments && message.attachments.length > 0 && (
            <div className="ax-attach-bar ax-attach-bar--history">
              {message.attachments.map((a) =>
                a.kind === 'image' ? (
                  <AttachmentThumb key={a.id} filename={a.filename} attachmentId={a.id} />
                ) : (
                  <AttachmentCard key={a.id} filename={a.filename} compact />
                ),
              )}
            </div>
          )}
          <div className="ax-user-bubble">
            {skillName ? (
              <>
                <span className="mr-1.5 inline-flex translate-y-[-1px] items-center gap-1 rounded-full bg-[var(--ax-info-bg)] px-2 py-0.5 align-middle font-mono text-[12px] font-medium text-[var(--ax-info-text)]">
                  <Zap className="size-3" />/{skillName}
                </span>
                {command?.[2] ?? ''}
              </>
            ) : (
              message.content
            )}
          </div>
        </div>
      </div>
    )
  }

  if (message.role === 'SYSTEM') {
    return (
      <div className="ax-message ax-message--system">
        <span className="ax-system-text">{message.content}</span>
      </div>
    )
  }

  if (message.role === 'TOOL') {
    return (
      <div className="ax-message ax-message--assistant">
        <div style={{ width: 28, flexShrink: 0 }} />
        <div className="ax-assistant-body">
          <ToolCallCard
            call={{ id: message.id, name: 'tool', result: message.content, done: true }}
          />
        </div>
      </div>
    )
  }

  // ASSISTANT
  const streaming = message.streaming === true
  // updatePlan 由输入框上方的计划面板独立呈现；文件生成用专属文件卡片；
  // askUserQuestion 用专属提问卡（实时经 SSE 帧，历史从 toolCalls 重建）
  const FILE_TOOLS = ['generateDocument', 'generateSpreadsheet']
  const allCalls = message.toolCalls ?? []
  const fileCalls = allCalls.filter((c) => FILE_TOOLS.includes(c.name))
  const toolCalls = allCalls.filter(
    (c) => c.name !== 'updatePlan' && c.name !== 'askUserQuestion' && !FILE_TOOLS.includes(c.name),
  )
  // 提问卡数据源：流式期间用 message.questions（SSE 帧驱动）；
  // 历史消息（刷新后）从 askUserQuestion 的 toolCalls 记录重建终态卡
  const liveQuestions = message.questions ?? []
  const historyQuestions =
    liveQuestions.length > 0
      ? []
      : allCalls
          // 仅重建已完成的调用：执行中（result 未至）的提问卡由 question-request 帧实时渲染
          .filter((c) => c.name === 'askUserQuestion' && c.result !== undefined)
          .map(toHistoryQuestion)
          .filter((q): q is QuestionItem => q !== null)
  const questionItems = [...liveQuestions, ...historyQuestions]
  const showActivity = streaming && !message.error

  return (
    <div className="ax-message ax-message--assistant">
      <div className="ax-assistant-avatar">
        <Logo size={28} />
      </div>
      <div className="ax-assistant-body">
        {message.reasoningContent ? (
          <ReasoningBlock
            content={message.reasoningContent}
            streaming={streaming && message.content === ''}
          />
        ) : null}
        <ToolCallGroup calls={toolCalls} />
        {fileCalls.map((c) => (
          <FileCard key={c.id} call={c} />
        ))}
        {/* 审批/提问卡按请求时刻的内容锚点嵌入正文流:等待作答时锚点即当时的
            内容末尾(卡片天然在最底部);作答后模型继续生成,新内容长在卡片下方,
            卡片随文融入不再钉底。历史重建无锚点的卡片渲染在正文之后。 */}
        {renderContentWithCards(message, questionItems)}
        {showActivity && <ActivityIndicator label={activityLabel(message)} />}
        {message.error ? (
          <div className="ax-msg-error" role="alert">
            {message.error.code && (
              <span className="ax-msg-error-code">{message.error.code}</span>
            )}
            {message.error.message}
            {/* 未落库的本地轮次（如登录过期 401 被过滤器拦截）可原文重发 */}
            {!streaming && message.id.startsWith('local-') && (
              <button
                type="button"
                className="ax-msg-error-retry"
                onClick={() => useChatStore.getState().resendFailed(message.id)}
              >
                <RotateCcw className="size-3" />
                重新发送
              </button>
            )}
          </div>
        ) : null}
        {message.ragSources && message.ragSources.length > 0 ? (
          <SourceList sources={message.ragSources} />
        ) : null}
        {message.tokenUsage && !streaming ? (
          <div className="ax-usage">
            tokens · 输入 {message.tokenUsage.promptTokens} / 输出{' '}
            {message.tokenUsage.completionTokens}
          </div>
        ) : null}
      </div>
    </div>
  )
}

/** 正文与审批/提问卡的交错渲染：有 contentOffset 的卡片按锚点切分正文嵌入原位，
    无锚点（历史重建）的卡片统一排在正文之后。同一锚点的多张卡片按原顺序连排。 */
function renderContentWithCards(message: ChatMessage, questionItems: QuestionItem[]) {
  const content = message.content ?? ''
  type Card = { key: string; offset: number | undefined; node: ReactNode }
  const cards: Card[] = [
    ...(message.approvals ?? []).map((item) => ({
      key: item.approvalId,
      offset: item.contentOffset,
      node: <ApprovalCard key={item.approvalId} item={item} />,
    })),
    ...questionItems.map((item) => ({
      key: item.questionId,
      offset: item.contentOffset,
      node: <QuestionCard key={item.questionId} item={item} />,
    })),
  ]
  const anchored = cards
    .filter((c): c is Card & { offset: number } => typeof c.offset === 'number')
    .sort((a, b) => a.offset - b.offset)
  const trailing = cards.filter((c) => typeof c.offset !== 'number')

  const nodes: ReactNode[] = []
  let cursor = 0
  for (const card of anchored) {
    const cut = Math.min(Math.max(card.offset, cursor), content.length)
    if (cut > cursor) {
      nodes.push(<MarkdownRenderer key={`seg-${cursor}`} content={content.slice(cursor, cut)} />)
      cursor = cut
    }
    nodes.push(card.node)
  }
  if (cursor < content.length) {
    nodes.push(<MarkdownRenderer key={`seg-${cursor}`} content={content.slice(cursor)} />)
  }
  nodes.push(...trailing.map((c) => c.node))
  return nodes
}

/** 历史消息的 askUserQuestion toolCall 记录 → 终态提问卡数据（解析失败返回 null 静默跳过） */
function toHistoryQuestion(call: ToolCallInfo): QuestionItem | null {
  try {
    const args =
      typeof call.args === 'string' ? (JSON.parse(call.args) as { questions?: QuestionSpec[] }) : null
    if (!args?.questions?.length) return null
    const result =
      typeof call.result === 'string'
        ? (JSON.parse(call.result) as { status?: string; answers?: QuestionAnswer[] })
        : null
    return {
      questionId: `history-${call.id}`,
      questions: args.questions,
      status: result?.status === 'answered' ? 'answered' : 'expired',
      answers: result?.answers ?? null,
    }
  } catch {
    return null
  }
}

export default memo(MessageItem)

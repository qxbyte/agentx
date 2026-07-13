import { memo } from 'react'
import type { ChatMessage } from '../types'
import ApprovalCard from './coding/ApprovalCard'
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
  if (message.role === 'USER') {
    return (
      <div className="ax-message ax-message--user">
        <div className="ax-user-bubble">{message.content}</div>
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
  const toolCalls = message.toolCalls ?? []
  const showCursor = streaming && !message.error

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
        {message.approvals?.map((item) => (
          <ApprovalCard key={item.approvalId} item={item} />
        ))}
        {message.content ? <MarkdownRenderer content={message.content} /> : null}
        {showCursor && <span className="ax-cursor" aria-hidden="true" />}
        {message.error ? (
          <div className="ax-msg-error" role="alert">
            {message.error.code && (
              <span className="ax-msg-error-code">{message.error.code}</span>
            )}
            {message.error.message}
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

export default memo(MessageItem)

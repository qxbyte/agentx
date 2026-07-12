import { ArrowUpOutlined, BorderOutlined } from '@ant-design/icons'
import { Tooltip } from 'antd'
import { useEffect, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'

interface ChatInputProps {
  streaming: boolean
  disabled?: boolean
  onSend: (content: string) => void
  onStop: () => void
}

const MAX_HEIGHT = 200

/** 底部输入区：自适应高度，Enter 发送 / Shift+Enter 换行，流式期间切换为停止按钮 */
export default function ChatInput({ streaming, disabled = false, onSend, onStop }: ChatInputProps) {
  const [value, setValue] = useState('')
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const composingRef = useRef(false)

  useEffect(() => {
    const el = textareaRef.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, MAX_HEIGHT)}px`
  }, [value])

  const submit = () => {
    const content = value.trim()
    if (!content || streaming || disabled) return
    onSend(content)
    setValue('')
    requestAnimationFrame(() => textareaRef.current?.focus())
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key !== 'Enter' || event.shiftKey) return
    // 中文输入法组词回车不发送
    if (composingRef.current || event.nativeEvent.isComposing) return
    event.preventDefault()
    submit()
  }

  const canSend = value.trim().length > 0 && !streaming && !disabled

  return (
    <div className="ax-composer">
      <textarea
        ref={textareaRef}
        rows={1}
        value={value}
        placeholder="给 AgentX 发送消息…"
        aria-label="消息输入框"
        disabled={disabled}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={handleKeyDown}
        onCompositionStart={() => {
          composingRef.current = true
        }}
        onCompositionEnd={() => {
          composingRef.current = false
        }}
      />
      {streaming ? (
        <Tooltip title="停止生成">
          <button
            type="button"
            className="ax-send-btn ax-send-btn--stop"
            aria-label="停止生成"
            onClick={onStop}
          >
            <BorderOutlined style={{ fontSize: 12 }} />
          </button>
        </Tooltip>
      ) : (
        <Tooltip title="发送">
          <button
            type="button"
            className="ax-send-btn"
            aria-label="发送"
            disabled={!canSend}
            onClick={submit}
          >
            <ArrowUpOutlined />
          </button>
        </Tooltip>
      )}
    </div>
  )
}

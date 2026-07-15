import { ArrowUp, FolderGit2, Square } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useChatStore } from '../stores/chat'
import InputToolbar from './coding/InputToolbar'
import KbPicker from './coding/KbPicker'
import ProjectPicker from './coding/ProjectPicker'
import { useKbOptions } from './coding/useKbOptions'

interface ChatInputProps {
  streaming: boolean
  disabled?: boolean
  onSend: (content: string) => void
  onStop: () => void
}

const MAX_HEIGHT = 200

/** 底部输入区：加高多行 + 模型/模式/工作区工具条；Enter 发送 / Shift+Enter 换行 */
export default function ChatInput({ streaming, disabled = false, onSend, onStop }: ChatInputProps) {
  const [value, setValue] = useState('')
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const composingRef = useRef(false)

  const workspaceId = useChatStore((s) => s.workspaceId)
  const activeConversationId = useChatStore((s) => s.activeConversationId)
  const projectLocked = useChatStore((s) => s.projectLocked)
  const projectName = useChatStore(
    (s) => s.projects.find((p) => p.id === s.workspaceId)?.name ?? null,
  )
  const lockedKbId = useChatStore((s) => s.kbIds[0] ?? null)
  const kbOptions = useKbOptions(projectLocked)
  const lockedKb = lockedKbId ? kbOptions.find((o) => o.id === lockedKbId) : null
  const lockedKbLabel = lockedKbId
    ? lockedKb
      ? lockedKb.name + (lockedKb.external ? '（外部）' : '')
      : '知识库'
    : '未绑定知识库'
  const coding = workspaceId !== null
  /** 新对话阶段：项目/知识库属于开场选择，会话开始后芯片隐藏 */
  const isNewConversation = activeConversationId === null

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
    if (composingRef.current || event.nativeEvent.isComposing) return
    event.preventDefault()
    submit()
  }

  const canSend = value.trim().length > 0 && !streaming && !disabled

  return (
    <div className="mx-auto max-w-[780px]">
      {/* 开场芯片托层（Codex 式）：仅新对话阶段显示，发送首条消息后消失。
          项目入口进入的对话为锁定态：只读展示归属与知识库，不可更改 */}
      {isNewConversation && (
        <div className="-mb-4 mx-4 flex items-center gap-1 rounded-t-[18px] bg-[var(--ax-chip-bg)] px-3 pb-6 pt-1.5">
          {projectLocked ? (
            <span
              className="flex h-7 items-center gap-1.5 px-2 text-xs text-[var(--ax-text-secondary)]"
              title="此对话属于该项目：归属与知识库沿用项目，不可更改"
            >
              <FolderGit2 className="size-3.5" />
              <span className="max-w-[160px] truncate font-medium text-foreground">
                {projectName ?? '项目'}
              </span>
              <span className="text-[var(--ax-text-faint)]">·</span>
              <span>{lockedKbLabel}</span>
            </span>
          ) : (
            <>
              <ProjectPicker />
              <KbPicker />
            </>
          )}
        </div>
      )}

      <div className="relative flex flex-col rounded-[26px] border border-[var(--ax-border)] bg-[var(--ax-surface)] transition-colors focus-within:border-[var(--ax-border-strong)]">

      <textarea
        ref={textareaRef}
        rows={1}
        value={value}
        placeholder={coding ? '描述需要处理的任务…' : '给 AgentX 发送消息…'}
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
        className="max-h-[200px] min-h-[52px] w-full resize-none bg-transparent px-4 pt-3 text-[14px] leading-relaxed text-foreground !outline-none focus:!outline-none focus-visible:!outline-none placeholder:text-[var(--ax-text-faint)]"
      />

      <div className="flex items-center gap-2 px-2.5 pb-2.5 pt-1">
        <InputToolbar />
        <div className="ml-auto">
          {streaming ? (
            <Tooltip>
              <TooltipTrigger asChild>
                <button
                  type="button"
                  className="ax-send-btn ax-send-btn--stop"
                  aria-label="停止生成"
                  onClick={onStop}
                >
                  <Square className="size-3 fill-current" />
                </button>
              </TooltipTrigger>
              <TooltipContent>停止生成</TooltipContent>
            </Tooltip>
          ) : (
            <Tooltip>
              <TooltipTrigger asChild>
                <button
                  type="button"
                  className="ax-send-btn"
                  aria-label="发送"
                  disabled={!canSend}
                  onClick={submit}
                >
                  <ArrowUp className="size-4" />
                </button>
              </TooltipTrigger>
              <TooltipContent>发送</TooltipContent>
            </Tooltip>
          )}
        </div>
      </div>
      </div>
    </div>
  )
}

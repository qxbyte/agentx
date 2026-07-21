import { ArrowUp, FileUp, FolderGit2, FolderUp, Plus, Square } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import type { ClipboardEvent, DragEvent, KeyboardEvent } from 'react'
import { toast } from 'sonner'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import Hint from '@/components/ui/hint'
import {
  collectFromDataTransfer,
  collectFromFileList,
  formatBytes,
  isCollectible,
  MAX_ATTACHMENT_FILES,
} from '../lib/attachments'
import type { AttachmentEntry } from '../lib/attachments'
import { compactConversation } from '../api/chat'
import { extractErrorMessage } from '../api/http'
import type { SkillMeta } from '../types'
import { useChatStore } from '../stores/chat'
import AttachmentCard from './AttachmentCard'
import ContextRing from './ContextRing'
import SkillMenu, { filterSkills } from './SkillMenu'
import AttachmentThumb from './AttachmentThumb'
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

/** 前端内置命令（非后端 skill）：发送时拦截执行，不进入对话 */
const BUILTIN_COMMANDS: SkillMeta[] = [
  {
    id: '__builtin_compact',
    name: 'compact',
    description: '压缩本会话早期上下文为摘要，释放模型窗口空间',
    argumentHint: null,
  },
]

/** 底部输入区：加高多行 + 模型/模式/工作区工具条；Enter 发送 / Shift+Enter 换行 */
export default function ChatInput({ streaming, disabled = false, onSend, onStop }: ChatInputProps) {
  const [value, setValue] = useState('')
  const [dragOver, setDragOver] = useState(false)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const dirInputRef = useRef<HTMLInputElement>(null)
  const composingRef = useRef(false)
  const dragDepthRef = useRef(0)

  const attachments = useChatStore((s) => s.attachments)
  const addAttachments = useChatStore((s) => s.addAttachments)
  const removeAttachment = useChatStore((s) => s.removeAttachment)
  const uploadingCount = attachments.filter((a) => a.status === 'uploading').length

  /* ---------- / 斜杠命令补全 ---------- */
  const skills = useChatStore((s) => s.skills)
  const loadSkills = useChatStore((s) => s.loadSkills)
  const [menuIndex, setMenuIndex] = useState(0)
  const [menuDismissed, setMenuDismissed] = useState(false)
  useEffect(() => {
    void loadSkills()
  }, [loadSkills])
  /** 正在敲命令名阶段（行首 / 且未出现空格/换行）才展示菜单;支持 plugin:skill 冒号命名空间 */
  const slashQuery = /^\/([a-z0-9-]*(?::[a-z0-9-]*)?)$/.exec(value)?.[1] ?? null
  const menuItems =
    slashQuery !== null && !menuDismissed
      ? filterSkills([...BUILTIN_COMMANDS, ...skills], slashQuery)
      : []
  const menuOpen = menuItems.length > 0
  useEffect(() => {
    setMenuIndex(0)
  }, [slashQuery])

  const applySkill = (name: string) => {
    // 回填 "/name " 尾随空格：菜单随之收起，光标就位继续敲参数
    setValue(`/${name} `)
    requestAnimationFrame(() => textareaRef.current?.focus())
  }

  /** 统一入口：按钮/粘贴/拖拽收集到的文件走同一限额与上传链路 */
  const ingest = (entries: AttachmentEntry[], skipped = 0) => {
    if (entries.length === 0 && skipped === 0) return
    const room = MAX_ATTACHMENT_FILES - useChatStore.getState().attachments.length
    if (entries.length > room || skipped > 0) {
      toast.info(
        entries.length > room
          ? `单条消息最多 ${MAX_ATTACHMENT_FILES} 个附件，已截取前 ${Math.max(room, 0)} 个`
          : `已跳过 ${skipped} 个不支持的文件（二进制/隐藏文件）`,
      )
    }
    if (room > 0 && entries.length > 0) void addAttachments(entries)
  }

  const workspaceId = useChatStore((s) => s.workspaceId)
  const activeConversationId = useChatStore((s) => s.activeConversationId)
  const contextUsage = useChatStore((s) => s.contextUsage)
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

  // 新建/切换会话时光标自动落入输入框(移动端不抢焦点,避免弹出软键盘)
  useEffect(() => {
    if (window.matchMedia('(pointer: coarse)').matches) return
    requestAnimationFrame(() => textareaRef.current?.focus())
  }, [activeConversationId])

  /** /compact 内置命令：调压缩接口，不进入对话 */
  const runCompact = () => {
    const conversationId = useChatStore.getState().activeConversationId
    if (!conversationId) {
      toast.info('当前还没有会话内容，无需压缩')
      return
    }
    setValue('')
    const pending = compactConversation(conversationId)
    toast.promise(pending, {
      loading: '正在压缩早期上下文…',
      success: (r) => {
        void useChatStore.getState().refreshContextUsage()
        return `已压缩 ${r.compactedMessages} 条早期消息为摘要（约 ${r.tokensBefore} → ${r.tokensAfter} tokens）`
      },
      error: (e) => extractErrorMessage(e, '压缩失败'),
    })
  }

  const submit = () => {
    const content = value.trim()
    if (!content || streaming || disabled || uploadingCount > 0) return
    if (content === '/compact') {
      runCompact()
      return
    }
    onSend(content)
    setValue('')
    requestAnimationFrame(() => textareaRef.current?.focus())
  }

  const handlePaste = (event: ClipboardEvent<HTMLTextAreaElement>) => {
    const files = Array.from(event.clipboardData?.files ?? [])
    if (files.length === 0) return
    event.preventDefault()
    const collectible = files.filter((f) => isCollectible(f.name))
    ingest(collectible.map((file) => ({ file })), files.length - collectible.length)
  }

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault()
    dragDepthRef.current = 0
    setDragOver(false)
    void collectFromDataTransfer(event.dataTransfer.items).then((entries) => ingest(entries))
  }

  const handleDragEnter = (event: DragEvent<HTMLDivElement>) => {
    if (!event.dataTransfer.types.includes('Files')) return
    event.preventDefault()
    dragDepthRef.current += 1
    setDragOver(true)
  }

  const handleDragLeave = () => {
    dragDepthRef.current = Math.max(0, dragDepthRef.current - 1)
    if (dragDepthRef.current === 0) setDragOver(false)
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    // 补全菜单打开时接管键盘：↑↓ 选择、Tab/Enter 回填、Esc 关闭（不触发发送）
    if (menuOpen) {
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault()
        const delta = event.key === 'ArrowDown' ? 1 : -1
        setMenuIndex((menuIndex + delta + menuItems.length) % menuItems.length)
        return
      }
      if (event.key === 'Tab' || (event.key === 'Enter' && !event.shiftKey)) {
        if (composingRef.current || event.nativeEvent.isComposing) return
        event.preventDefault()
        const selected = menuItems[menuIndex]
        if (selected) applySkill(selected.name)
        return
      }
      if (event.key === 'Escape') {
        event.preventDefault()
        setMenuDismissed(true)
        return
      }
    }
    if (event.key !== 'Enter' || event.shiftKey) return
    if (composingRef.current || event.nativeEvent.isComposing) return
    event.preventDefault()
    submit()
  }

  const canSend = value.trim().length > 0 && !streaming && !disabled && uploadingCount === 0

  return (
    <div className="mx-auto max-w-[780px]">
      {/* 开场芯片托层：仅新对话阶段显示，发送首条消息后消失。
          项目入口进入的对话为锁定态：只读展示归属与知识库，不可更改 */}
      {isNewConversation && (
        <div className="-mb-4 mx-4 flex items-center gap-1 rounded-t-[18px] bg-[var(--ax-chip-bg)] px-3 pb-6 pt-1.5">
          {projectLocked ? (
            <Hint text="此对话属于该项目：归属与知识库沿用项目，不可更改">
            <span className="flex h-7 items-center gap-1.5 px-2 text-xs text-[var(--ax-text-secondary)]">
              <FolderGit2 className="size-3.5" />
              <span className="max-w-[160px] truncate font-medium text-foreground">
                {projectName ?? '项目'}
              </span>
              <span className="text-[var(--ax-text-faint)]">·</span>
              <span>{lockedKbLabel}</span>
            </span>
            </Hint>
          ) : (
            <>
              <ProjectPicker />
              <KbPicker />
            </>
          )}
        </div>
      )}

      <div
        className={`relative flex flex-col rounded-[26px] border bg-[var(--ax-surface)] transition-colors focus-within:border-[var(--ax-border-strong)] ${
          dragOver
            ? 'border-dashed border-[var(--ax-border-strong)] bg-[var(--ax-hover-weak)]'
            : 'border-[var(--ax-border)]'
        }`}
        onDragEnter={handleDragEnter}
        onDragOver={(e) => e.preventDefault()}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
      >
      {/* / 斜杠命令补全菜单：composer 上方浮层 */}
      {menuOpen && (
        <SkillMenu
          items={menuItems}
          activeIndex={menuIndex}
          onSelect={applySkill}
          onHover={setMenuIndex}
        />
      )}
      {/* 隐藏文件选择器：+ 菜单触发 */}
      <input
        ref={fileInputRef}
        type="file"
        multiple
        className="hidden"
        onChange={(e) => {
          const files = Array.from(e.target.files ?? [])
          const collectible = files.filter((f) => isCollectible(f.name))
          ingest(collectible.map((file) => ({ file })), files.length - collectible.length)
          e.target.value = ''
        }}
      />
      <input
        ref={dirInputRef}
        type="file"
        className="hidden"
        {...({ webkitdirectory: '' } as Record<string, string>)}
        onChange={(e) => {
          if (e.target.files) ingest(collectFromFileList(e.target.files))
          e.target.value = ''
        }}
      />

      {/* 附件卡片条：textarea 上方 flex-wrap */}
      {attachments.length > 0 && (
        <div className="ax-attach-bar">
          {attachments.map((a) =>
            a.kind === 'image' && a.status !== 'failed' ? (
              <AttachmentThumb
                key={a.key}
                filename={a.filename}
                {...(a.previewUrl ? { previewUrl: a.previewUrl } : {})}
                uploading={a.status === 'uploading'}
                onRemove={() => removeAttachment(a.key)}
              />
            ) : (
              <AttachmentCard
                key={a.key}
                filename={a.filename}
                subtitle={
                  a.status === 'failed'
                    ? (a.error ?? '上传失败')
                    : a.relPath
                      ? a.relPath
                      : `${formatBytes(a.sizeBytes)}${a.truncated ? ' · 已截断' : ''}`
                }
                uploading={a.status === 'uploading'}
                failed={a.status === 'failed'}
                onRemove={() => removeAttachment(a.key)}
              />
            ),
          )}
        </div>
      )}

      <textarea
        ref={textareaRef}
        rows={1}
        value={value}
        placeholder={coding ? '描述需要处理的任务…' : '给 AgentX 发送消息…'}
        aria-label="消息输入框"
        disabled={disabled}
        onChange={(e) => {
          setValue(e.target.value)
          setMenuDismissed(false)
        }}
        onKeyDown={handleKeyDown}
        onPaste={handlePaste}
        onCompositionStart={() => {
          composingRef.current = true
        }}
        onCompositionEnd={() => {
          composingRef.current = false
        }}
        className="max-h-[200px] min-h-[52px] w-full resize-none bg-transparent px-4 pt-3 text-[14px] leading-relaxed text-foreground !outline-none !shadow-none focus:!outline-none focus:!shadow-none focus-visible:!outline-none focus-visible:!shadow-none placeholder:text-[var(--ax-text-faint)]"
      />

      <div className="flex items-center gap-2 px-2.5 pb-2.5 pt-1">
        {/* 左下角 + ：添加文件 / 文件夹。不挂 Tooltip——与菜单触发器同按钮时，
            菜单关闭后 Radix 回焦会以焦点态误弹提示（鼠标不在也显示）；说明改进菜单内 */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button type="button" className="ax-attach-add" aria-label="添加附件">
              <Plus className="size-4" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start" side="top">
            <DropdownMenuItem onClick={() => fileInputRef.current?.click()}>
              <FileUp className="size-4" />
              添加文件
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => dirInputRef.current?.click()}>
              <FolderUp className="size-4" />
              添加文件夹
            </DropdownMenuItem>
            <div className="px-2 pb-1 pt-0.5 text-[11px] text-[var(--ax-text-faint)]">
              也可直接粘贴或拖入文件
            </div>
          </DropdownMenuContent>
        </DropdownMenu>
        <InputToolbar />
        <div className="ml-auto flex items-center gap-2.5">
          {/* 上下文余量指示环：有会话且已加载用量才显示 */}
          {activeConversationId && contextUsage && contextUsage.tokens > 0 && (
            <ContextRing usage={contextUsage} />
          )}
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

import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import * as chatApi from '../../api/chat'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { cn } from '@/lib/utils'
import { useChatStore } from '../../stores/chat'
import type { CodingMode, ModelOption } from '../../types'

const DEFAULT_MODEL = '__default__'

/** 三模式颜色语义：Plan 蓝（只读安全）/ Ask 黑白（默认审批）/ Auto 琥珀（自动执行需留意） */
const MODES: { value: CodingMode; label: string; hint: string; activeClass: string }[] = [
  { value: 'PLAN', label: 'Plan', hint: '只读规划，不改动', activeClass: 'text-[var(--ax-accent)]' },
  { value: 'ASK', label: 'Ask', hint: '逐操作审批', activeClass: 'text-foreground' },
  { value: 'AUTO', label: 'Auto', hint: '无需审批，自动执行', activeClass: 'text-[var(--ax-warning)]' },
]

/**
 * 输入框底部工具条：模型 · 模式（会话内可随时切换）。
 * 项目/知识库属于开场选择，见输入框上方芯片（ProjectPicker/KbPicker）。
 */
export default function InputToolbar() {
  const modelConfigId = useChatStore((s) => s.modelConfigId)
  const workspaceId = useChatStore((s) => s.workspaceId)
  const codingMode = useChatStore((s) => s.codingMode)
  const setModelConfigId = useChatStore((s) => s.setModelConfigId)
  const setCodingMode = useChatStore((s) => s.setCodingMode)

  const [models, setModels] = useState<ModelOption[]>([])

  useEffect(() => {
    void chatApi.listChatModels().then(setModels).catch(() => setModels([]))
  }, [])

  /* 滑块药丸：随选中按钮测量定位；位移用 CSS 回弹过渡，切换时重放果冻挤压动画 */
  const modesRef = useRef<HTMLDivElement>(null)
  const thumbRef = useRef<HTMLSpanElement>(null)
  const btnRefs = useRef<Partial<Record<CodingMode, HTMLButtonElement | null>>>({})
  const [thumb, setThumb] = useState<{ left: number; width: number } | null>(null)
  const mountedRef = useRef(false)

  useLayoutEffect(() => {
    const btn = btnRefs.current[codingMode]
    if (!btn) return
    setThumb({ left: btn.offsetLeft, width: btn.offsetWidth })
    // 首次挂载只定位不弹跳；之后每次切换重放果冻动画
    if (!mountedRef.current) {
      mountedRef.current = true
      return
    }
    const el = thumbRef.current
    if (!el) return
    el.classList.remove('ax-jelly')
    void el.offsetWidth // 强制 reflow，让同名动画可重放
    el.classList.add('ax-jelly')
  }, [codingMode])

  const coding = workspaceId !== null

  return (
    <div className="flex flex-wrap items-center gap-0.5">
      {/* 模型 */}
      <Select
        value={modelConfigId ?? DEFAULT_MODEL}
        onValueChange={(v) => setModelConfigId(v === DEFAULT_MODEL ? null : v)}
      >
        {/* 极简触发器：纯文字，无图标无箭头（用户定稿） */}
        <SelectTrigger className="h-6 w-auto gap-1 rounded-full border-none bg-transparent px-2 text-[11px] text-muted-foreground hover:bg-accent focus:ring-0 [&>svg]:hidden">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={DEFAULT_MODEL}>默认模型</SelectItem>
          {models.map((m) => (
            <SelectItem key={m.id} value={m.id}>
              {m.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      {/* 模式：始终可选，选择项目后对该项目生效。
          滑块药丸独立于按钮，切换时非线性位移（回弹贝塞尔）+ 果冻挤压动画 */}
      <div
        ref={modesRef}
        className="relative ml-1 flex items-center gap-0.5 rounded-full bg-muted p-0.5"
        title={coding ? '编码模式' : '编码模式（选择项目后对该项目生效）'}
      >
        {thumb && (
          <span
            ref={thumbRef}
            aria-hidden="true"
            className="ax-mode-thumb"
            style={{ left: thumb.left, width: thumb.width }}
          />
        )}
        {MODES.map((m) => (
          <button
            key={m.value}
            ref={(el) => {
              btnRefs.current[m.value] = el
            }}
            type="button"
            onClick={() => setCodingMode(m.value)}
            title={m.hint}
            className={cn(
              'relative z-[1] rounded-full px-2 py-0.5 text-[11px] font-medium transition-colors',
              codingMode === m.value ? m.activeClass : 'text-muted-foreground hover:text-foreground',
            )}
          >
            {m.label}
          </button>
        ))}
      </div>
    </div>
  )
}

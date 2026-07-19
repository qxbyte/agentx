import { Shield, ShieldOff } from 'lucide-react'
import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import * as chatApi from '../../api/chat'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import Hint from '@/components/ui/hint'
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

/** 滑块三档。Bypass 不是并列档位，而是 Auto 的「解除限制」修饰态（盾牌开关进入）。 */
const MODES: { value: CodingMode; label: string; hint: string; activeClass: string }[] = [
  { value: 'PLAN', label: 'Plan', hint: '只读规划，不改动', activeClass: 'text-[var(--ax-accent)]' },
  { value: 'ASK', label: 'Ask', hint: '逐操作审批', activeClass: 'text-foreground' },
  { value: 'AUTO', label: 'Auto', hint: '无需审批，自动执行（限工作区内）', activeClass: 'text-[var(--ax-warning)]' },
]

/**
 * 输入框底部工具条：模式（左固定）· 模型（右靠齐）。
 * Bypass 进入路径刻意设了三步门槛（选 Auto → 点盾牌 → 弹窗确认），防误触；
 * 激活后 Auto 药丸变红、盾牌点亮，再点盾牌立即回落普通 Auto。
 */
export default function InputToolbar() {
  const modelConfigId = useChatStore((s) => s.modelConfigId)
  const workspaceId = useChatStore((s) => s.workspaceId)
  const codingMode = useChatStore((s) => s.codingMode)
  const setModelConfigId = useChatStore((s) => s.setModelConfigId)
  const setCodingMode = useChatStore((s) => s.setCodingMode)

  const [models, setModels] = useState<ModelOption[]>([])
  const [bypassConfirmOpen, setBypassConfirmOpen] = useState(false)

  useEffect(() => {
    void chatApi.listChatModels().then(setModels).catch(() => setModels([]))
  }, [])

  const bypass = codingMode === 'BYPASS'
  /** 滑块上的显示档位：BYPASS 落在 Auto 药丸上（红色态） */
  const displayMode: CodingMode = bypass ? 'AUTO' : codingMode

  /* 滑块药丸：随选中按钮测量定位；位移用 CSS 回弹过渡，切换时重放果冻挤压动画 */
  const modesRef = useRef<HTMLDivElement>(null)
  const thumbRef = useRef<HTMLSpanElement>(null)
  const btnRefs = useRef<Partial<Record<CodingMode, HTMLButtonElement | null>>>({})
  const [thumb, setThumb] = useState<{ left: number; width: number } | null>(null)
  const mountedRef = useRef(false)

  useLayoutEffect(() => {
    const btn = btnRefs.current[displayMode]
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
  }, [codingMode, displayMode])

  const coding = workspaceId !== null

  return (
    <div className="flex flex-1 flex-wrap items-center gap-0.5">
      {/* 模式在前、位置固定（紧跟 + 号）：模型名长短不再推挤模式药丸。
          滑块药丸独立于按钮，切换时非线性位移（回弹贝塞尔）+ 果冻挤压动画 */}
      <div
        ref={modesRef}
        className="relative ml-1 flex items-center gap-0.5 rounded-full bg-muted p-0.5"
        aria-label={coding ? '编码模式' : '编码模式（选择项目后对该项目生效）'}
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
          <Hint key={m.value} text={m.value === 'AUTO' && bypass ? '已完全放行（点盾牌恢复）' : m.hint}>
            <button
              ref={(el) => {
                btnRefs.current[m.value] = el
              }}
              type="button"
              onClick={() => setCodingMode(m.value)}
              className={cn(
                'relative z-[1] rounded-full px-2 py-0.5 text-[11px] font-medium transition-colors',
                displayMode === m.value
                  ? m.value === 'AUTO' && bypass
                    ? 'text-[var(--ax-danger)]'
                    : m.activeClass
                  : 'text-muted-foreground hover:text-foreground',
              )}
            >
              {m.label}
            </button>
          </Hint>
        ))}
      </div>

      {/* Bypass 盾牌开关：仅停留在 Auto（含放行态）时出现 */}
      {displayMode === 'AUTO' && (
        <Hint
          text={
            bypass
              ? '已完全放行：无路径边界、无命令黑名单。点击恢复 Auto'
              : '解除全部限制（Bypass）：可读写本机任意文件、执行任意命令'
          }
        >
          <button
            type="button"
            aria-label={bypass ? '恢复 Auto 模式' : '解除限制（Bypass）'}
            onClick={() => (bypass ? setCodingMode('AUTO') : setBypassConfirmOpen(true))}
            className={cn(
              'ml-0.5 flex size-6 items-center justify-center rounded-full transition-colors',
              bypass
                ? 'bg-[var(--ax-danger-bg)] text-[var(--ax-danger)]'
                : 'text-muted-foreground hover:bg-accent hover:text-foreground',
            )}
          >
            {bypass ? <ShieldOff className="size-3.5" /> : <Shield className="size-3.5" />}
          </button>
        </Hint>
      )}

      {/* Bypass 确认门槛：明确列出解除的边界，确认才生效 */}
      <AlertDialog open={bypassConfirmOpen} onOpenChange={setBypassConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>解除全部限制？</AlertDialogTitle>
            <AlertDialogDescription>
              Bypass 模式下，模型将不再询问审批，且解除两道硬约束：可读写本机任意文件
              （不限于项目目录），可执行任意命令（仅保留毁机级命令拦截）。
              等同于你本人在终端里操作，误操作无法由平台兜底。仅在明确信任当前任务时开启。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              className="bg-[var(--ax-danger)] text-white hover:bg-[var(--ax-danger)]/90"
              onClick={() => setCodingMode('BYPASS')}
            >
              解除限制
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* 模型右靠齐：占据剩余空间的右端，与右侧余量环/发送按钮相邻 */}
      <Select
        value={modelConfigId ?? DEFAULT_MODEL}
        onValueChange={(v) => setModelConfigId(v === DEFAULT_MODEL ? null : v)}
      >
        {/* 极简触发器：纯文字，无图标无箭头（用户定稿） */}
        <SelectTrigger className="ml-auto h-6 w-auto gap-1 rounded-full border-none bg-transparent px-2 text-[11px] text-muted-foreground hover:bg-accent focus:ring-0 [&>svg]:hidden">
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
    </div>
  )
}

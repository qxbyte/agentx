import { CheckCircle2, ChevronDown, ChevronRight, Loader2, Wrench } from 'lucide-react'
import { useState } from 'react'
import ToolResultCard from './coding/ToolResultCard'
import ToolCallCard from './ToolCallCard'
import type { ToolCallInfo } from '../types'

/** 超过该数量时折叠为集合条 */
const COLLAPSE_THRESHOLD = 3
/** 收起态展示的最近调用条数 */
const TAIL_COUNT = 2

function renderCard(call: ToolCallInfo) {
  return call.kind ? (
    <ToolResultCard key={call.id} call={call} />
  ) : (
    <ToolCallCard key={call.id} call={call} />
  )
}

/**
 * 工具调用集合：少量直接平铺；多个默认收起为一条摘要
 * （操作总数 + 最近 2 个灰字），点击展开查看全部、再点收起。
 */
export default function ToolCallGroup({ calls }: { calls: ToolCallInfo[] }) {
  const [open, setOpen] = useState(false)

  if (calls.length === 0) return null
  if (calls.length <= COLLAPSE_THRESHOLD) {
    return <>{calls.map(renderCard)}</>
  }

  if (open) {
    // 展开态：单一整体块——头部可收起，内部行式列表（分隔线相连），行内仍可展开单个详情
    return (
      <div className="ax-toolcall mb-2.5">
        <button
          type="button"
          onClick={() => setOpen(false)}
          className="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] transition-colors hover:bg-black/[0.015]"
        >
          <Wrench className="size-4 shrink-0 text-muted-foreground" />
          <span className="font-medium">{calls.length} 个操作</span>
          <span className="ml-auto inline-flex items-center gap-1 text-xs text-muted-foreground">
            收起
            <ChevronDown className="size-3.5" />
          </span>
        </button>
        <div className="divide-y divide-[var(--ax-border-subtle)] border-t border-[var(--ax-border-subtle)] [&_.ax-toolcall]:mb-0 [&_.ax-toolcall]:rounded-none [&_.ax-toolcall]:border-0">
          {calls.map(renderCard)}
        </div>
      </div>
    )
  }

  const tail = calls.slice(-TAIL_COUNT)
  const hidden = calls.length - tail.length
  const running = calls.some((c) => !(c.done === true || c.result !== undefined))

  return (
    <button
      type="button"
      onClick={() => setOpen(true)}
      className="ax-toolcall mb-2.5 block w-full text-left transition-colors hover:bg-black/[0.015]"
      aria-expanded={false}
    >
      <div className="flex items-center gap-2 px-3 py-2 text-[13px]">
        <Wrench className="size-4 shrink-0 text-muted-foreground" />
        <span className="font-medium">{calls.length} 个操作</span>
        {running ? (
          <Loader2 className="size-3.5 animate-spin text-muted-foreground" />
        ) : (
          <CheckCircle2 className="size-3.5 text-[var(--ax-success)]" />
        )}
        <span className="ml-auto inline-flex items-center gap-1 text-xs text-muted-foreground">
          {hidden > 0 && <span>已隐藏 {hidden} 个</span>}
          <ChevronRight className="size-3.5" />
        </span>
      </div>
      <div className="flex flex-col gap-0.5 px-3 pb-2">
        {tail.map((c) => {
          const finished = c.done === true || c.result !== undefined
          return (
            <span
              key={c.id}
              className="flex items-center gap-1.5 font-mono text-xs text-muted-foreground"
            >
              {finished ? (
                <span className="size-1.5 rounded-full bg-[var(--ax-success)]/60" />
              ) : (
                <Loader2 className="size-3 animate-spin" />
              )}
              {c.name}
            </span>
          )
        })}
      </div>
    </button>
  )
}

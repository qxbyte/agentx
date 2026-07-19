import { CheckCircle2, ChevronDown, ChevronRight, Loader2, Wrench } from 'lucide-react'
import { useState } from 'react'
import { ToolCallBody } from './coding/ToolResultCard'
import { argHint, runningPurpose, summarizeToolCalls } from '../lib/toolCallSummary'
import { cn } from '@/lib/utils'
import type { ToolCallInfo } from '../types'

/** 超过该数量时折叠为集合条 */
const COLLAPSE_THRESHOLD = 3
/** 收起态展示的最近调用条数 */
const TAIL_COUNT = 2

function isFinished(c: ToolCallInfo): boolean {
  return c.done === true || c.result !== undefined
}

/** 集合内的紧凑行：矮行 + 小字 mono + 主参数提示，点击展开该调用详情。 */
function CompactRow({ call }: { call: ToolCallInfo }) {
  const [open, setOpen] = useState(false)
  const finished = isFinished(call)
  const hint = argHint(call)
  return (
    <div>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex h-7 w-full items-center gap-2 px-3 text-left transition-colors hover:bg-[var(--ax-hover-weak)]"
      >
        {finished ? (
          <span className="size-1.5 shrink-0 rounded-full bg-[var(--ax-success)]/70" />
        ) : (
          <Loader2 className="size-3 shrink-0 animate-spin text-muted-foreground" />
        )}
        <span className="shrink-0 font-mono text-xs text-foreground/80">{call.name}</span>
        {hint && (
          <span className="truncate font-mono text-[11px] text-muted-foreground">{hint}</span>
        )}
        <span className="ml-auto flex shrink-0 items-center gap-1 text-[11px] text-muted-foreground">
          {finished ? '已完成' : '运行中'}
          {open ? <ChevronDown className="size-3" /> : <ChevronRight className="size-3" />}
        </span>
      </button>
      {open && (
        <div className="px-3 pb-2 text-xs">
          <ToolCallBody call={call} kind={call.kind ?? 'generic'} />
        </div>
      )}
    </div>
  )
}

/**
 * 工具调用集合：少量平铺紧凑行；多个默认收起为归类摘要条——
 * 头部展示这批调用「做了什么」（按语义分类计数，如「搜索 ×3 · 读取 ×4 · 修改文件 ×2」）
 * + 最近 2 个调用（含主参数）+ 隐藏数；展开为单一整体块内的紧凑行列表。
 */
export default function ToolCallGroup({ calls }: { calls: ToolCallInfo[] }) {
  const [open, setOpen] = useState(false)

  if (calls.length === 0) return null

  const collapsible = calls.length > COLLAPSE_THRESHOLD
  const running = calls.some((c) => !isFinished(c))
  // 运行中优先显示当前动作自述（模型经 purpose 参数说明这步在干什么）；完成后回归归类摘要
  const current = running ? runningPurpose(calls) : null
  const summary = current ? `正在：${current}` : summarizeToolCalls(calls)

  // 少量：直接一个整体块平铺紧凑行
  if (!collapsible) {
    return (
      <div className="ax-toolcall mb-2.5 divide-y divide-[var(--ax-border-subtle)]">
        {calls.map((c) => (
          <CompactRow key={c.id} call={c} />
        ))}
      </div>
    )
  }

  if (open) {
    return (
      <div className="ax-toolcall mb-2.5">
        <button
          type="button"
          onClick={() => setOpen(false)}
          className="flex h-8 w-full items-center gap-2 px-3 text-left text-xs transition-colors hover:bg-[var(--ax-hover-weak)]"
        >
          <Wrench className="size-3.5 shrink-0 text-muted-foreground" />
          <span className="truncate font-medium">{summary}</span>
          <span className="shrink-0 text-[11px] text-muted-foreground">{calls.length} 个操作</span>
          <span className="ml-auto inline-flex shrink-0 items-center gap-1 text-[11px] text-muted-foreground">
            收起
            <ChevronDown className="size-3" />
          </span>
        </button>
        <div className="divide-y divide-[var(--ax-border-subtle)] border-t border-[var(--ax-border-subtle)]">
          {calls.map((c) => (
            <CompactRow key={c.id} call={c} />
          ))}
        </div>
      </div>
    )
  }

  const tail = calls.slice(-TAIL_COUNT)
  const hidden = calls.length - tail.length

  return (
    <button
      type="button"
      onClick={() => setOpen(true)}
      className="ax-toolcall mb-2.5 block w-full text-left transition-colors hover:bg-[var(--ax-hover-weak)]"
    >
      <div className="flex h-8 items-center gap-2 px-3 text-xs">
        <Wrench className="size-3.5 shrink-0 text-muted-foreground" />
        <span className="truncate font-medium">{summary}</span>
        <span className="shrink-0 text-[11px] text-muted-foreground">{calls.length} 个操作</span>
        {running ? (
          <Loader2 className="size-3 shrink-0 animate-spin text-muted-foreground" />
        ) : (
          <CheckCircle2 className="size-3.5 shrink-0 text-[var(--ax-success)]" />
        )}
        <span className="ml-auto inline-flex shrink-0 items-center gap-1 text-[11px] text-muted-foreground">
          {hidden > 0 && <span>已隐藏 {hidden} 个</span>}
          <ChevronRight className="size-3" />
        </span>
      </div>
      <div className={cn('flex flex-col gap-0.5 px-3 pb-1.5')}>
        {tail.map((c) => {
          const hint = argHint(c)
          return (
            <span
              key={c.id}
              className="flex items-center gap-1.5 font-mono text-[11px] text-muted-foreground"
            >
              {isFinished(c) ? (
                <span className="size-1.5 shrink-0 rounded-full bg-[var(--ax-success)]/60" />
              ) : (
                <Loader2 className="size-3 shrink-0 animate-spin" />
              )}
              <span className="shrink-0">{c.name}</span>
              {hint && <span className="truncate opacity-70">{hint}</span>}
            </span>
          )
        })}
      </div>
    </button>
  )
}

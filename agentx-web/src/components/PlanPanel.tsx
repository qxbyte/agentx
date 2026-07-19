import { Check, ChevronDown, ListTodo, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import type { PlanState } from '../types'

interface PlanPanelProps {
  plan: PlanState
  onDismiss: () => void
}

/**
 * 任务清单面板：固定在输入框上方，随 SSE 实时更新。
 * 收起时单行显示进度与当前步骤；全部完成后自动收起，可手动关闭。
 */
export default function PlanPanel({ plan, onDismiss }: PlanPanelProps) {
  const steps = plan.steps
  const done = steps.filter((s) => s.status === 'completed').length
  const total = steps.length
  const allDone = done === total
  const current =
    steps.find((s) => s.status === 'in_progress') ?? steps.find((s) => s.status === 'pending')

  const [open, setOpen] = useState(!allDone)
  // 全部完成的瞬间自动收起（仅触发一次，不锁死用户手动展开）
  const prevAllDone = useRef(allDone)
  useEffect(() => {
    if (allDone && !prevAllDone.current) setOpen(false)
    prevAllDone.current = allDone
  }, [allDone])

  return (
    <div className="ax-plan">
      <button
        type="button"
        className="ax-plan-header"
        onClick={() => setOpen(!open)}
        aria-expanded={open}
      >
        <ListTodo className="size-3 shrink-0" />
        <span className="ax-plan-title">{plan.title ?? '任务清单'}</span>
        <span className="ax-plan-count">
          {done}/{total}
        </span>
        {/* 折叠单行优先展示进行时形态（activeForm，如「正在运行测试」） */}
        <span className="ax-plan-current">
          {!open && (allDone ? '已全部完成' : (current?.activeForm ?? current?.step))}
        </span>
        <ChevronDown className={`ax-plan-chevron size-3.5${open ? '' : ' ax-plan-chevron--closed'}`} />
        <span
          role="button"
          tabIndex={0}
          className="ax-plan-dismiss"
          aria-label="关闭计划面板"
          onClick={(e) => {
            e.stopPropagation()
            onDismiss()
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.stopPropagation()
              onDismiss()
            }
          }}
        >
          <X className="size-3.5" />
        </span>
      </button>
      {open && (
        <ol className="ax-plan-steps ax-scroll">
          {steps.map((s, i) => (
            <li key={i} className={`ax-plan-step ax-plan-step--${s.status}`}>
              <span className="ax-plan-mark">
                {s.status === 'completed' ? (
                  <Check className="size-2.5" strokeWidth={3} />
                ) : s.status === 'in_progress' ? (
                  <span className="ax-plan-dot" />
                ) : null}
              </span>
              <span className="ax-plan-text">{s.step}</span>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}

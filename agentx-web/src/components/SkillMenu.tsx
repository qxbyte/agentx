import { useEffect, useRef } from 'react'
import { cn } from '@/lib/utils'
import type { SkillMeta } from '../types'

interface SkillMenuProps {
  items: SkillMeta[]
  activeIndex: number
  onSelect: (name: string) => void
  onHover: (index: number) => void
}

/** 按 query 过滤补全项：name 前缀命中优先，子串命中次之 */
export function filterSkills(skills: SkillMeta[], query: string): SkillMeta[] {
  const q = query.toLowerCase()
  const prefix = skills.filter((s) => s.name.startsWith(q))
  const substr = skills.filter((s) => !s.name.startsWith(q) && s.name.includes(q))
  return [...prefix, ...substr]
}

/**
 * / 斜杠命令补全菜单（对标 Claude Code）：composer 上方浮层。
 * 纯展示组件——过滤与键盘导航（↑↓/Tab/Enter/Esc）由 ChatInput 持有。
 */
export default function SkillMenu({ items, activeIndex, onSelect, onHover }: SkillMenuProps) {
  const itemRefs = useRef<(HTMLButtonElement | null)[]>([])

  // ↑↓ 移动选中项时让滚动容器跟随（'nearest' 已可见时不动，悬停不引发跳动）
  useEffect(() => {
    itemRefs.current[activeIndex]?.scrollIntoView({ block: 'nearest' })
  }, [activeIndex])

  if (items.length === 0) return null
  return (
    <div className="absolute inset-x-0 bottom-full z-20 mb-2 overflow-hidden rounded-2xl border border-[var(--ax-border)] bg-[var(--ax-surface)] shadow-lg">
      <div className="ax-scroll max-h-[264px] overflow-y-auto p-1">
        {items.map((skill, i) => (
          <button
            key={skill.id}
            type="button"
            ref={(el) => {
              itemRefs.current[i] = el
            }}
            // mousedown 阻止默认，保持焦点留在输入框
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => onSelect(skill.name)}
            onMouseEnter={() => onHover(i)}
            className={cn(
              'flex w-full items-baseline gap-2 rounded-md px-2.5 py-1.5 text-left transition-colors',
              i === activeIndex && 'bg-accent',
            )}
          >
            <span
              className={cn(
                'shrink-0 font-mono text-[13px] font-semibold',
                i === activeIndex ? 'text-[var(--ax-accent)]' : 'text-foreground',
              )}
            >
              /{skill.name}
            </span>
            {skill.argumentHint && (
              <span className="shrink-0 font-mono text-xs text-[var(--ax-text-faint)]">
                {skill.argumentHint}
              </span>
            )}
            {skill.description && (
              <span
                className={cn(
                  'min-w-0 flex-1 truncate text-xs',
                  i === activeIndex ? 'text-[var(--ax-accent)]' : 'text-[var(--ax-text-secondary)]',
                )}
              >
                {skill.description}
              </span>
            )}
          </button>
        ))}
      </div>
    </div>
  )
}

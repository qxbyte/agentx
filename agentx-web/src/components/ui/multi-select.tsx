import { Check, ChevronDown, X } from 'lucide-react'
import { useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'

export interface MultiSelectOption {
  value: string
  label: string
}

interface MultiSelectProps {
  options: MultiSelectOption[]
  value: string[]
  onChange: (value: string[]) => void
  placeholder?: string
  /** 已选项标签渲染（默认取 option.label；列表外的值回退为原值） */
  renderTag?: (value: string) => string
  /** 弹层展开方向：贴近弹窗底部的字段用 'top' 向上展开，避免被弹窗底边裁掉
      （portalled=false 就地渲染不会自动翻转，需显式指定） */
  side?: 'top' | 'bottom'
}

/** 轻量多选：触发器展示已选 Badge，弹层内搜索 + 复选。替代 antd Select mode="multiple"。 */
export function MultiSelect({
  options,
  value,
  onChange,
  placeholder = '请选择',
  renderTag,
  side = 'bottom',
}: MultiSelectProps) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')

  const toggle = (v: string) => {
    onChange(value.includes(v) ? value.filter((x) => x !== v) : [...value, v])
  }

  const labelOf = (v: string) =>
    renderTag?.(v) ?? options.find((o) => o.value === v)?.label ?? v

  const filtered = query
    ? options.filter((o) => o.label.toLowerCase().includes(query.toLowerCase()))
    : options

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          type="button"
          className="flex min-h-9 w-full items-center justify-between gap-2 rounded-2xl border border-input bg-background px-3 py-1.5 text-left text-sm transition-colors focus:outline-none focus:border-[var(--ax-border-strong)]"
        >
          {value.length === 0 ? (
            <span className="text-muted-foreground">{placeholder}</span>
          ) : (
            <span className="flex flex-wrap gap-1">
              {value.map((v) => (
                <Badge key={v} variant="default" className="gap-1">
                  {labelOf(v)}
                  <span
                    role="button"
                    tabIndex={-1}
                    onClick={(e) => {
                      e.stopPropagation()
                      toggle(v)
                    }}
                    className="opacity-60 hover:opacity-100"
                  >
                    <X className="size-3" />
                  </span>
                </Badge>
              ))}
            </span>
          )}
          <ChevronDown className="size-4 shrink-0 opacity-50" />
        </button>
      </PopoverTrigger>
      <PopoverContent
        portalled={false}
        side={side}
        align="start"
        className="w-(--radix-popover-trigger-width) p-0"
      >
        <div className="border-b border-border p-2">
          <input
            className="h-8 w-full rounded-lg bg-transparent px-2 text-sm outline-none placeholder:text-muted-foreground"
            placeholder="搜索…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        <div className="ax-scroll max-h-60 overflow-y-auto p-1">
          {filtered.length === 0 ? (
            <div className="px-2 py-6 text-center text-sm text-muted-foreground">无匹配项</div>
          ) : (
            filtered.map((o) => {
              const checked = value.includes(o.value)
              return (
                <button
                  key={o.value}
                  type="button"
                  onClick={() => toggle(o.value)}
                  className={cn(
                    'flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm transition-colors hover:bg-accent',
                    checked && 'bg-accent/50',
                  )}
                >
                  <span
                    className={cn(
                      'flex size-4 shrink-0 items-center justify-center rounded border',
                      checked ? 'border-primary bg-primary text-primary-foreground' : 'border-input',
                    )}
                  >
                    {checked && <Check className="size-3" />}
                  </span>
                  <span className="truncate">{o.label}</span>
                </button>
              )
            })
          )}
        </div>
      </PopoverContent>
    </Popover>
  )
}

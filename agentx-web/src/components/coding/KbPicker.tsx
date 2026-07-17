import { BookOpen, Check } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'
import { listEnabledExternalKbs } from '../../api/externalKb'
import * as kbApi from '../../api/kb'
import { useChatStore } from '../../stores/chat'

interface KbOption {
  id: string
  name: string
  external: boolean
}

/**
 * 知识库多选（紧凑）：本地库与启用中的外部库并列。
 * 知识库是会话创建期属性——仅新对话首条消息前可选（芯片只在新对话阶段显示），
 * 发送后随会话固化；未选择则该对话绝不引入知识库召回。
 */
export default function KbPicker() {
  const kbIds = useChatStore((s) => s.kbIds)
  const setKbIds = useChatStore((s) => s.setKbIds)
  const [options, setOptions] = useState<KbOption[]>([])

  useEffect(() => {
    void Promise.allSettled([kbApi.listKbs(), listEnabledExternalKbs()]).then(([locals, exts]) => {
      const opts: KbOption[] = []
      if (locals.status === 'fulfilled') {
        opts.push(...locals.value.map((k) => ({ id: k.id, name: k.name, external: false })))
      }
      if (exts.status === 'fulfilled') {
        opts.push(...exts.value.map((k) => ({ id: k.id, name: k.name, external: true })))
      }
      setOptions(opts)
    })
  }, [])

  const toggle = (id: string) => {
    setKbIds(kbIds.includes(id) ? kbIds.filter((x) => x !== id) : [...kbIds, id])
  }

  const active = kbIds.length > 0

  return (
    <Popover>
      <PopoverTrigger asChild>
        <button
          type="button"
          title="选择检索用的知识库（发送首条消息后随会话固化）"
          className={cn(
            'flex h-7 items-center gap-1.5 rounded-full px-2.5 text-xs transition-colors hover:bg-[var(--ax-hover)]',
            active ? 'font-medium text-foreground' : 'text-[var(--ax-text-secondary)]',
          )}
        >
          <BookOpen className="size-3.5" />
          <span>知识库</span>
          {active && (
            <span className="rounded-full bg-primary px-1.5 text-[10px] font-medium text-primary-foreground">
              {kbIds.length}
            </span>
          )}
        </button>
      </PopoverTrigger>
      <PopoverContent side="top" align="start" className="w-60 p-1">
        {options.length === 0 ? (
          <div className="px-2 py-6 text-center text-xs text-muted-foreground">
            暂无可用知识库（本地在「知识库」页创建，外部在设置里接入）
          </div>
        ) : (
          <div className="ax-scroll max-h-60 overflow-y-auto">
            {options.map((kb) => {
              const checked = kbIds.includes(kb.id)
              return (
                <button
                  key={kb.id}
                  type="button"
                  onClick={() => toggle(kb.id)}
                  className={cn(
                    'flex w-full items-center gap-2 rounded-md px-2 py-1 text-left text-[13px] transition-colors hover:bg-accent',
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
                  <span className="min-w-0 flex-1 truncate">{kb.name}</span>
                  {kb.external && (
                    <span className="shrink-0 rounded-full bg-[var(--ax-info-bg)] px-1.5 text-[10px] text-[var(--ax-info-text)]">
                      外部
                    </span>
                  )}
                </button>
              )
            })}
          </div>
        )}
      </PopoverContent>
    </Popover>
  )
}

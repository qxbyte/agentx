import { BookOpen, Check } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'
import * as kbApi from '../../api/kb'
import { useChatStore } from '../../stores/chat'
import type { KnowledgeBase } from '../../types'

/** 知识库多选（紧凑）：独立于项目，指定本次检索追加的知识库。 */
export default function KbPicker() {
  const kbIds = useChatStore((s) => s.kbIds)
  const setKbIds = useChatStore((s) => s.setKbIds)
  const [kbs, setKbs] = useState<KnowledgeBase[]>([])

  useEffect(() => {
    void kbApi.listKbs().then(setKbs).catch(() => setKbs([]))
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
          className={cn(
            'flex h-7 items-center gap-1.5 rounded-full px-2.5 text-xs transition-colors hover:bg-black/[0.06]',
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
      <PopoverContent align="start" className="w-56 p-1">
        {kbs.length === 0 ? (
          <div className="px-2 py-6 text-center text-xs text-muted-foreground">暂无知识库</div>
        ) : (
          <div className="ax-scroll max-h-60 overflow-y-auto">
            {kbs.map((kb) => {
              const checked = kbIds.includes(kb.id)
              return (
                <button
                  key={kb.id}
                  type="button"
                  onClick={() => toggle(kb.id)}
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
                  <span className="truncate">{kb.name}</span>
                </button>
              )
            })}
          </div>
        )}
      </PopoverContent>
    </Popover>
  )
}

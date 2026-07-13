import { Cpu } from 'lucide-react'
import { useEffect, useState } from 'react'
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

const MODES: { value: CodingMode; label: string; hint: string }[] = [
  { value: 'PLAN', label: 'Plan', hint: '只读规划，不改动' },
  { value: 'ASK', label: 'Ask', hint: '逐操作审批' },
  { value: 'AUTO', label: 'Auto', hint: '无需审批，自动执行' },
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

  const coding = workspaceId !== null

  return (
    <div className="flex flex-wrap items-center gap-0.5">
      {/* 模型 */}
      <Select
        value={modelConfigId ?? DEFAULT_MODEL}
        onValueChange={(v) => setModelConfigId(v === DEFAULT_MODEL ? null : v)}
      >
        <SelectTrigger className="h-6 w-auto gap-1 rounded-full border-none bg-transparent px-2 text-[11px] text-muted-foreground hover:bg-accent focus:ring-0 [&>svg]:size-3">
          <Cpu className="size-3" />
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

      {/* 模式：始终可选，选择项目后对该项目生效 */}
      <div
        className="ml-1 flex items-center gap-0.5 rounded-full bg-muted p-0.5"
        title={coding ? '编码模式' : '编码模式（选择项目后对该项目生效）'}
      >
        {MODES.map((m) => (
          <button
            key={m.value}
            type="button"
            onClick={() => setCodingMode(m.value)}
            title={m.hint}
            className={cn(
              'rounded-full px-2 py-0.5 text-[11px] font-medium transition-colors',
              codingMode === m.value
                ? 'bg-background text-foreground shadow-sm'
                : 'text-muted-foreground hover:text-foreground',
            )}
          >
            {m.label}
          </button>
        ))}
      </div>
    </div>
  )
}

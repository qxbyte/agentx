import { Check, Loader2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { cn } from '@/lib/utils'
import * as agentsApi from '../api/agents'
import * as chatApi from '../api/chat'
import { extractErrorMessage } from '../api/http'
import * as kbApi from '../api/kb'
import type { AgentView, KnowledgeBase } from '../types'

const NONE = '__none__'

interface NewChatModalProps {
  open: boolean
  onClose: () => void
  /** 会话创建成功后回调（调用方负责路由跳转与列表刷新） */
  onCreated: (conversationId: string) => void
}

/** 新建对话弹窗：可选绑定 Agent（单选）与知识库（多选） */
export default function NewChatModal({ open, onClose, onCreated }: NewChatModalProps) {
  const [loading, setLoading] = useState(false)
  const [creating, setCreating] = useState(false)
  const [agents, setAgents] = useState<AgentView[]>([])
  const [kbs, setKbs] = useState<KnowledgeBase[]>([])
  const [agentId, setAgentId] = useState<string>(NONE)
  const [kbIds, setKbIds] = useState<string[]>([])

  useEffect(() => {
    if (!open) return
    setAgentId(NONE)
    setKbIds([])
    setLoading(true)
    void Promise.allSettled([agentsApi.listAgents(), kbApi.listKbs()]).then(
      ([agentsResult, kbsResult]) => {
        setAgents(
          agentsResult.status === 'fulfilled' ? agentsResult.value.filter((a) => a.enabled) : [],
        )
        setKbs(kbsResult.status === 'fulfilled' ? kbsResult.value : [])
        setLoading(false)
      },
    )
  }, [open])

  const toggleKb = (id: string) => {
    setKbIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))
  }

  const handleOk = async () => {
    setCreating(true)
    try {
      const conversation = await chatApi.createConversation({
        ...(agentId !== NONE ? { agentId } : {}),
        ...(kbIds.length > 0 ? { kbIds } : {}),
      })
      onClose()
      onCreated(conversation.id)
    } catch (error) {
      toast.error(extractErrorMessage(error, '创建对话失败'))
    } finally {
      setCreating(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>新建对话</DialogTitle>
        </DialogHeader>
        {loading ? (
          <div className="flex justify-center py-8">
            <Loader2 className="size-5 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <div className="flex flex-col gap-4 py-2">
            <div>
              <div className="ax-field-label">Agent（可选）</div>
              <Select value={agentId} onValueChange={setAgentId}>
                <SelectTrigger>
                  <SelectValue placeholder="不选择则使用默认对话模式" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE}>不绑定（默认对话模式）</SelectItem>
                  {agents.map((a) => (
                    <SelectItem key={a.id} value={a.id}>
                      {a.description ? `${a.name} — ${a.description}` : a.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div>
              <div className="ax-field-label">知识库（可选，支持多选）</div>
              {kbs.length === 0 ? (
                <div className="rounded-xl border border-border px-3 py-6 text-center text-sm text-muted-foreground">
                  暂无知识库
                </div>
              ) : (
                <div className="ax-scroll max-h-52 overflow-y-auto rounded-xl border border-border p-1">
                  {kbs.map((kb) => {
                    const checked = kbIds.includes(kb.id)
                    return (
                      <button
                        key={kb.id}
                        type="button"
                        onClick={() => toggleKb(kb.id)}
                        className={cn(
                          'flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-sm transition-colors hover:bg-accent',
                          checked && 'bg-accent/60',
                        )}
                      >
                        <span
                          className={cn(
                            'flex size-4 shrink-0 items-center justify-center rounded border',
                            checked
                              ? 'border-primary bg-primary text-primary-foreground'
                              : 'border-input',
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
            </div>
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            取消
          </Button>
          <Button onClick={() => void handleOk()} disabled={creating || loading}>
            {creating && <Loader2 className="size-4 animate-spin" />}
            创建
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

import { Check, Loader2, ShieldAlert, X } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { extractErrorMessage } from '../../api/http'
import { useChatStore } from '../../stores/chat'
import type { ApprovalItem } from '../../types'
import DiffView from './DiffView'
import ShellOutput from './ShellOutput'

/** 审批卡：危险操作执行前的人工确认。按 kind 内嵌预览，批准/拒绝回传后台。 */
export default function ApprovalCard({ item }: { item: ApprovalItem }) {
  const resolveApproval = useChatStore((s) => s.resolveApproval)
  const [busy, setBusy] = useState(false)
  const pending = item.status === 'pending'

  const decide = async (approved: boolean) => {
    setBusy(true)
    try {
      await resolveApproval(item.approvalId, approved)
    } catch (error) {
      toast.error(extractErrorMessage(error, '操作失败，请重试'))
    } finally {
      setBusy(false)
    }
  }

  const KIND_LABEL: Record<string, string> = {
    patch: '应用代码补丁',
    shell: '执行命令',
    write: '写入文件',
    commit: '提交变更',
  }

  return (
    <div
      className={cardClass(item.status)}
    >
      <div className="flex items-center gap-2 px-3 py-2">
        <ShieldAlert className="size-4 text-warning" />
        <span className="text-sm font-medium">{KIND_LABEL[item.kind] ?? '待确认操作'}</span>
        <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-[11px] text-muted-foreground">
          {item.toolName}
        </code>
        <StatusBadge status={item.status} />
      </div>

      <div className="px-3 pb-3">
        <Preview item={item} />
      </div>

      {pending && (
        <div className="flex items-center justify-end gap-2 border-t border-border px-3 py-2">
          <Button variant="outline" size="sm" disabled={busy} onClick={() => void decide(false)}>
            <X className="size-3.5" />
            拒绝
          </Button>
          <Button size="sm" disabled={busy} onClick={() => void decide(true)}>
            {busy ? <Loader2 className="size-3.5 animate-spin" /> : <Check className="size-3.5" />}
            批准
          </Button>
        </div>
      )}
    </div>
  )
}

function cardClass(status: ApprovalItem['status']): string {
  const base = 'mb-2.5 overflow-hidden rounded-xl border'
  if (status === 'approved') return `${base} border-[#cde9de] bg-[#f2faf6]`
  if (status === 'rejected') return `${base} border-[#efd7d5] bg-[#fbf3f2]`
  return `${base} border-[#eee1c8] bg-[#fdfaf3]`
}

function StatusBadge({ status }: { status: ApprovalItem['status'] }) {
  if (status === 'pending') return null
  const approved = status === 'approved'
  return (
    <span
      className={`ml-auto rounded-full px-2 py-0.5 text-xs font-medium ${
        approved ? 'bg-[#e9f6f1] text-[#0e8a6e]' : 'bg-[#fbf3f2] text-destructive'
      }`}
    >
      {approved ? '已批准' : '已拒绝'}
    </span>
  )
}

function Preview({ item }: { item: ApprovalItem }) {
  const p = item.preview
  switch (item.kind) {
    case 'patch':
      return <DiffView diff={p.diff ?? ''} />
    case 'shell':
      return <ShellOutput command={p.command} />
    case 'write':
      return (
        <div className="overflow-hidden rounded-xl border border-border">
          <div className="border-b border-border bg-muted/30 px-3 py-1.5 font-mono text-xs">
            {p.path}
          </div>
          <pre className="ax-scroll m-0 max-h-[260px] overflow-auto bg-[var(--ax-code-bg)] px-3 py-2 font-mono text-xs">
            {p.content}
          </pre>
        </div>
      )
    case 'commit':
      return (
        <div className="rounded-lg bg-muted/40 px-3 py-2 text-sm">
          <span className="text-muted-foreground">提交信息：</span>
          {p.message}
        </div>
      )
    default:
      return <pre className="ax-toolcall-pre ax-scroll">{p.args ?? JSON.stringify(p, null, 2)}</pre>
  }
}

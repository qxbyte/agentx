import { CheckCircle2, Loader2, XCircle } from 'lucide-react'
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
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { extractErrorMessage } from '../../api/http'
import * as codingApi from '../../api/coding'
import type { Workspace, WorkspaceValidation } from '../../types'
import { useKbOptions } from './useKbOptions'

const NONE = '__none__'

interface WorkspaceFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 传入则为编辑，否则新建 */
  editing?: Workspace | null
  /** 保存成功回调（新建/更新后的实体） */
  onSaved: (ws: Workspace) => void
}

/** 工作区（项目）创建/编辑对话框——工作区管理页与输入框「新建项目」共用（DRY）。 */
export default function WorkspaceFormDialog({
  open,
  onOpenChange,
  editing,
  onSaved,
}: WorkspaceFormDialogProps) {
  const kbs = useKbOptions(open)
  const [name, setName] = useState('')
  const [rootPath, setRootPath] = useState('')
  const [kbId, setKbId] = useState(NONE)
  const [errors, setErrors] = useState<{ name?: string; rootPath?: string }>({})
  const [validation, setValidation] = useState<WorkspaceValidation | null>(null)
  const [validating, setValidating] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) return
    setName(editing?.name ?? '')
    setRootPath(editing?.rootPath ?? '')
    setKbId(editing?.kbId ?? NONE)
    setErrors({})
    setValidation(null)
  }, [open, editing])

  const doValidate = async () => {
    if (!rootPath.trim()) {
      setErrors((e) => ({ ...e, rootPath: '请输入目录路径' }))
      return
    }
    setValidating(true)
    try {
      setValidation(await codingApi.validateWorkspacePath(rootPath.trim()))
    } catch (error) {
      toast.error(extractErrorMessage(error, '校验失败'))
    } finally {
      setValidating(false)
    }
  }

  const handleSave = async () => {
    const next: { name?: string; rootPath?: string } = {}
    if (!name.trim()) next.name = '请输入名称'
    if (!rootPath.trim()) next.rootPath = '请输入目录路径'
    if (Object.keys(next).length > 0) {
      setErrors(next)
      return
    }
    const payload: codingApi.WorkspacePayload = {
      name: name.trim(),
      rootPath: rootPath.trim(),
      kbId: kbId === NONE ? null : kbId,
    }
    setSaving(true)
    try {
      const ws = editing
        ? await codingApi.updateWorkspace(editing.id, payload)
        : await codingApi.createWorkspace(payload)
      toast.success(editing ? '项目已更新' : '项目已创建')
      onOpenChange(false)
      onSaved(ws)
    } catch (error) {
      toast.error(extractErrorMessage(error, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{editing ? '编辑项目' : '新建项目'}</DialogTitle>
        </DialogHeader>
        <div className="mt-1 flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="wf-name">名称</Label>
            <Input
              id="wf-name"
              placeholder="例如：订单服务"
              maxLength={60}
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
            {errors.name && <p className="text-xs text-destructive">{errors.name}</p>}
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="wf-path">本地目录（后端服务器上的绝对路径）</Label>
            <div className="flex gap-2">
              <Input
                id="wf-path"
                className="font-mono"
                placeholder="/Users/you/project"
                value={rootPath}
                onChange={(e) => {
                  setRootPath(e.target.value)
                  setValidation(null)
                }}
              />
              <Button variant="outline" disabled={validating} onClick={() => void doValidate()}>
                {validating ? <Loader2 className="size-4 animate-spin" /> : null}
                校验
              </Button>
            </div>
            {errors.rootPath && <p className="text-xs text-destructive">{errors.rootPath}</p>}
            {validation && (
              <div className="flex flex-wrap items-center gap-3 rounded-lg bg-muted/40 px-3 py-2 text-xs">
                <ValFlag ok={validation.exists} label="存在" />
                <ValFlag ok={validation.writable} label="可写" />
                <ValFlag ok={validation.gitRepo} label="git 仓库" />
                <span className="text-muted-foreground">{validation.message}</span>
              </div>
            )}
          </div>
          <div className="flex flex-col gap-1.5">
            <Label>默认知识库（可选，输入框可再改）</Label>
            <Select value={kbId} onValueChange={setKbId}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>不绑定</SelectItem>
                {kbs.map((kb) => (
                  <SelectItem key={kb.id} value={kb.id}>
                    {kb.name}
                    {kb.external ? '（外部）' : ''}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={() => void handleSave()} disabled={saving}>
            {saving && <Loader2 className="size-4 animate-spin" />}
            保存
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function ValFlag({ ok, label }: { ok: boolean; label: string }) {
  return (
    <span className={`inline-flex items-center gap-1 ${ok ? 'text-[var(--ax-ok-text)]' : 'text-destructive'}`}>
      {ok ? <CheckCircle2 className="size-3.5" /> : <XCircle className="size-3.5" />}
      {label}
    </span>
  )
}

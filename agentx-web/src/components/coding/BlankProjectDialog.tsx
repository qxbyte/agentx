import { Loader2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
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
import * as kbApi from '../../api/kb'
import type { KnowledgeBase, Workspace } from '../../types'

const NONE = '__none__'

interface BlankProjectDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onCreated: (ws: Workspace) => void
}

/** 新建空白项目（Codex 式）：只填名字，目录由后端在受控根下创建并 git init。 */
export default function BlankProjectDialog({ open, onOpenChange, onCreated }: BlankProjectDialogProps) {
  const [kbs, setKbs] = useState<KnowledgeBase[]>([])
  const [name, setName] = useState('')
  const [kbId, setKbId] = useState(NONE)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) return
    void kbApi.listKbs().then(setKbs).catch(() => setKbs([]))
    setName('')
    setKbId(NONE)
    setError(null)
  }, [open])

  const handleCreate = async () => {
    if (!name.trim()) {
      setError('请输入项目名')
      return
    }
    setSaving(true)
    try {
      const ws = await codingApi.createBlankWorkspace({
        name: name.trim(),
        kbId: kbId === NONE ? null : kbId,
      })
      toast.success(`空白项目已创建：${ws.rootPath}`)
      onOpenChange(false)
      onCreated(ws)
    } catch (err) {
      toast.error(extractErrorMessage(err, '创建失败'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>新建空白项目</DialogTitle>
          <DialogDescription>在服务器受控目录下创建空项目并初始化 git 仓库</DialogDescription>
        </DialogHeader>
        <div className="mt-1 flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="bp-name">项目名</Label>
            <Input
              id="bp-name"
              placeholder="例如：my-new-app"
              maxLength={60}
              autoFocus
              value={name}
              onChange={(e) => {
                setName(e.target.value)
                setError(null)
              }}
              onKeyDown={(e) => {
                if (e.key === 'Enter') void handleCreate()
              }}
            />
            {error && <p className="text-xs text-destructive">{error}</p>}
          </div>
          <div className="flex flex-col gap-1.5">
            <Label>默认知识库（可选）</Label>
            <Select value={kbId} onValueChange={setKbId}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>不绑定</SelectItem>
                {kbs.map((kb) => (
                  <SelectItem key={kb.id} value={kb.id}>
                    {kb.name}
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
          <Button onClick={() => void handleCreate()} disabled={saving}>
            {saving && <Loader2 className="size-4 animate-spin" />}
            创建
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

import { Loader2, Pencil, Plus, SquareSlash, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
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
import { Switch } from '@/components/ui/switch'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { extractErrorMessage } from '../../api/http'
import * as skillsApi from '../../api/skills'
import AppShell from '../../components/AppShell'
import ErrorState from '../../components/ErrorState'
import { useChatStore } from '../../stores/chat'
import type { SkillPayload, SkillView } from '../../types'

const NAME_PATTERN = /^[a-z0-9-]{1,64}$/

interface SkillForm {
  name: string
  description: string
  argumentHint: string
  content: string
  userInvocable: boolean
  modelInvocable: boolean
}

const EMPTY_FORM: SkillForm = {
  name: '',
  description: '',
  argumentHint: '',
  content: '',
  userInvocable: true,
  modelInvocable: true,
}

function toPayload(form: SkillForm): SkillPayload {
  return {
    name: form.name.trim(),
    description: form.description.trim(),
    ...(form.argumentHint.trim() ? { argumentHint: form.argumentHint.trim() } : {}),
    content: form.content,
    userInvocable: form.userInvocable,
    modelInvocable: form.modelInvocable,
  }
}

/** 技能（斜杠命令）管理：用户自有数据，输入框敲 /name 触发展开 */
export default function SkillsPage() {
  const [items, setItems] = useState<SkillView[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<SkillView | null>(null)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState<SkillForm>(EMPTY_FORM)
  const [formError, setFormError] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<SkillView | null>(null)

  const patch = (p: Partial<SkillForm>) => setForm((prev) => ({ ...prev, ...p }))
  /** 变更后刷新聊天输入框的 / 补全菜单缓存 */
  const refreshMenu = () => void useChatStore.getState().loadSkills()

  const refresh = async () => {
    try {
      setItems(await skillsApi.listSkills())
      setLoadError(null)
    } catch (error) {
      setLoadError(extractErrorMessage(error, '加载技能列表失败'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refresh()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    setEditing(null)
    setForm(EMPTY_FORM)
    setFormError(null)
    setModalOpen(true)
  }

  /** 编辑：列表不含 content（渐进式披露 L2），打开时按需拉详情 */
  const openEdit = async (skill: SkillView) => {
    try {
      const detail = await skillsApi.getSkill(skill.id)
      setEditing(skill)
      setForm({
        name: detail.name,
        description: detail.description,
        argumentHint: detail.argumentHint ?? '',
        content: detail.content,
        userInvocable: detail.userInvocable,
        modelInvocable: detail.modelInvocable,
      })
      setFormError(null)
      setModalOpen(true)
    } catch (error) {
      toast.error(extractErrorMessage(error, '加载技能详情失败'))
    }
  }

  const handleSave = async () => {
    if (!NAME_PATTERN.test(form.name.trim())) {
      setFormError('名称仅允许小写字母、数字、连字符，长度 1-64')
      return
    }
    if (!form.description.trim()) {
      setFormError('请填写描述（补全菜单展示，也是未来模型自动触发的判据）')
      return
    }
    if (!form.content.trim()) {
      setFormError('请填写指令内容')
      return
    }
    setSaving(true)
    try {
      if (editing) {
        await skillsApi.updateSkill(editing.id, toPayload(form))
        toast.success('技能已更新')
      } else {
        await skillsApi.createSkill(toPayload(form))
        toast.success(`技能已创建，输入框敲 /${form.name.trim()} 即可使用`)
      }
      setModalOpen(false)
      refreshMenu()
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  const handleToggle = async (skill: SkillView, enabled: boolean) => {
    setItems((prev) => prev.map((s) => (s.id === skill.id ? { ...s, enabled } : s)))
    try {
      await skillsApi.setSkillEnabled(skill.id, enabled)
      refreshMenu()
    } catch (error) {
      setItems((prev) => prev.map((s) => (s.id === skill.id ? { ...s, enabled: !enabled } : s)))
      toast.error(extractErrorMessage(error, '操作失败'))
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) return
    try {
      await skillsApi.deleteSkill(deleteTarget.id)
      toast.success('已删除')
      setDeleteTarget(null)
      refreshMenu()
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, '删除失败'))
    }
  }

  return (
    <AppShell
      title="技能"
      extra={
        <Button onClick={openCreate}>
          <Plus className="size-4" />
          新建技能
        </Button>
      }
    >
      {loadError ? (
        <ErrorState
          message={loadError}
          onRetry={() => {
            setLoading(true)
            void refresh()
          }}
        />
      ) : loading ? (
        <div className="flex animate-pulse flex-col gap-3 py-6">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-8 w-full rounded bg-muted" />
          ))}
        </div>
      ) : items.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-16 text-center">
          <SquareSlash className="size-8 text-muted-foreground" />
          <p className="text-sm text-muted-foreground">
            还没有技能。技能是可复用的指令模板——创建后在输入框敲 / 即可快速调用
          </p>
          <Button onClick={openCreate}>
            <Plus className="size-4" />
            新建技能
          </Button>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[200px]">命令</TableHead>
              <TableHead>描述</TableHead>
              <TableHead className="w-[180px]">参数提示</TableHead>
              <TableHead className="w-[80px]">启用</TableHead>
              <TableHead className="w-[100px]">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map((skill) => (
              <TableRow key={skill.id}>
                <TableCell className="font-mono text-[13px] font-medium">
                  /{skill.name}
                  {!skill.userInvocable && (
                    <span className="ml-1.5 rounded-full bg-[var(--ax-hover)] px-1.5 py-0.5 font-sans text-[10px] font-normal text-muted-foreground">
                      仅模型
                    </span>
                  )}
                </TableCell>
                <TableCell className="max-w-0 truncate text-muted-foreground">
                  {skill.description}
                </TableCell>
                <TableCell className="max-w-0 truncate font-mono text-xs text-muted-foreground">
                  {skill.argumentHint ?? '—'}
                </TableCell>
                <TableCell>
                  <Switch
                    checked={skill.enabled}
                    onCheckedChange={(checked) => void handleToggle(skill, checked)}
                  />
                </TableCell>
                <TableCell>
                  <div className="flex items-center">
                    <Button
                      variant="ghost"
                      size="icon"
                      className="size-7 text-[var(--ax-ios-blue)] hover:text-[var(--ax-ios-blue)]"
                      onClick={() => void openEdit(skill)}
                    >
                      <Pencil className="size-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="size-7 text-destructive hover:text-destructive"
                      onClick={() => setDeleteTarget(skill)}
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <Dialog open={modalOpen} onOpenChange={setModalOpen}>
        <DialogContent className="max-h-[85vh] max-w-[640px] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editing ? '编辑技能' : '新建技能'}</DialogTitle>
          </DialogHeader>
          <div className="mt-1 flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="s-name">名称</Label>
              <Input
                id="s-name"
                placeholder="例如：translate（将以 /translate 调用）"
                maxLength={64}
                value={form.name}
                onChange={(e) => patch({ name: e.target.value })}
              />
              <p className="text-xs text-muted-foreground">小写字母、数字、连字符</p>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="s-desc">描述</Label>
              <Input
                id="s-desc"
                placeholder="这个技能做什么、什么时候用？"
                maxLength={200}
                value={form.description}
                onChange={(e) => patch({ description: e.target.value })}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="s-hint">参数提示（可选）</Label>
              <Input
                id="s-hint"
                placeholder="例如：[要翻译的文本]"
                maxLength={255}
                value={form.argumentHint}
                onChange={(e) => patch({ argumentHint: e.target.value })}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="s-content">指令内容</Label>
              <Textarea
                id="s-content"
                rows={8}
                className="rounded-lg font-mono text-[13px]"
                placeholder={'发送 /名称 时展开的指令模板…\n\n占位符：$ARGUMENTS = 命令后的全部参数；$1..$9 = 按空格切分的位置参数'}
                maxLength={8000}
                value={form.content}
                onChange={(e) => patch({ content: e.target.value })}
              />
              <p className="text-xs text-muted-foreground">
                支持 $ARGUMENTS 与 $1..$9 参数占位符
              </p>
            </div>
            <div className="flex flex-col gap-3 rounded-lg border border-[var(--ax-border)] p-3">
              <div className="flex items-center justify-between gap-4">
                <div className="flex flex-col gap-0.5">
                  <Label htmlFor="s-user-invocable">用户可调用（user-invocable）</Label>
                  <p className="text-xs text-muted-foreground">
                    关闭后不出现在 / 菜单、输入命令也不展开——仅供模型自动调用的内部技能
                  </p>
                </div>
                <Switch
                  id="s-user-invocable"
                  checked={form.userInvocable}
                  onCheckedChange={(checked) => patch({ userInvocable: checked })}
                />
              </div>
              <div className="flex items-center justify-between gap-4">
                <div className="flex flex-col gap-0.5">
                  <Label htmlFor="s-model-invocable">允许模型自动调用</Label>
                  <p className="text-xs text-muted-foreground">
                    关闭即 disable-model-invocation：模型不会按描述自动使用（适合有副作用的命令）；
                    模型自动调用能力即将上线
                  </p>
                </div>
                <Switch
                  id="s-model-invocable"
                  checked={form.modelInvocable}
                  onCheckedChange={(checked) => patch({ modelInvocable: checked })}
                />
              </div>
            </div>
            {formError && <p className="text-xs text-destructive">{formError}</p>}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setModalOpen(false)}>
              取消
            </Button>
            <Button onClick={() => void handleSave()} disabled={saving}>
              {saving && <Loader2 className="size-4 animate-spin" />}
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={deleteTarget !== null} onOpenChange={(o) => !o && setDeleteTarget(null)}>
        <AlertDialogContent className="max-w-md">
          <AlertDialogHeader>
            <AlertDialogTitle>删除技能</AlertDialogTitle>
            <AlertDialogDescription>
              确定删除「/{deleteTarget?.name}」吗？已发送的历史消息不受影响。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => void handleDelete()}
            >
              删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </AppShell>
  )
}

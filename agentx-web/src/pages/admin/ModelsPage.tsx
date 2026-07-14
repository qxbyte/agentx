import { Loader2, Pencil, Plus, Star, Trash2 } from 'lucide-react'
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
import { Badge } from '@/components/ui/badge'
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
import { Switch } from '@/components/ui/switch'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import * as adminApi from '../../api/admin'
import { extractErrorMessage } from '../../api/http'
import ErrorState from '../../components/ErrorState'
import PageHeader from '../../components/PageHeader'
import type { ModelConfig, ModelConfigPayload, ModelType, ProviderType } from '../../types'

const PROVIDER_OPTIONS: { value: ProviderType; label: string }[] = [
  { value: 'DEEPSEEK', label: 'DeepSeek' },
  { value: 'OPENAI_COMPATIBLE', label: 'OpenAI 兼容' },
  { value: 'OLLAMA', label: 'Ollama' },
]

const TYPE_OPTIONS: { value: ModelType; label: string }[] = [
  { value: 'CHAT', label: '对话（CHAT）' },
  { value: 'EMBEDDING', label: '向量（EMBEDDING）' },
  { value: 'RERANK', label: '重排（RERANK）' },
]

interface ModelForm {
  name: string
  providerType: ProviderType
  type: ModelType
  baseUrl: string
  modelName: string
  apiKey: string
  enabled: boolean
}

const EMPTY_FORM: ModelForm = {
  name: '',
  providerType: 'OPENAI_COMPATIBLE',
  type: 'CHAT',
  baseUrl: '',
  modelName: '',
  apiKey: '',
  enabled: true,
}

/** 从已有记录构造 PUT 全量 payload（不带 apiKey 表示不修改密钥） */
function toPayload(record: ModelConfig, overrides?: Partial<ModelConfigPayload>): ModelConfigPayload {
  return {
    name: record.name,
    providerType: record.providerType,
    baseUrl: record.baseUrl,
    modelName: record.modelName,
    type: record.type,
    enabled: record.enabled,
    ...overrides,
  }
}

export default function ModelsPage() {
  const [configs, setConfigs] = useState<ModelConfig[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ModelConfig | null>(null)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState<ModelForm>(EMPTY_FORM)
  const [errors, setErrors] = useState<Partial<Record<keyof ModelForm, string>>>({})
  const [deleteTarget, setDeleteTarget] = useState<ModelConfig | null>(null)

  const patch = (p: Partial<ModelForm>) => setForm((prev) => ({ ...prev, ...p }))

  const refresh = async () => {
    try {
      setConfigs(await adminApi.listModelConfigs())
      setLoadError(null)
    } catch (error) {
      setLoadError(extractErrorMessage(error, '加载模型配置失败'))
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
    setErrors({})
    setModalOpen(true)
  }

  const openEdit = (record: ModelConfig) => {
    setEditing(record)
    setForm({
      name: record.name,
      providerType: record.providerType,
      type: record.type,
      baseUrl: record.baseUrl,
      modelName: record.modelName,
      apiKey: '',
      enabled: record.enabled,
    })
    setErrors({})
    setModalOpen(true)
  }

  const handleSave = async () => {
    const next: Partial<Record<keyof ModelForm, string>> = {}
    if (!form.name.trim()) next.name = '请输入名称'
    if (!form.baseUrl.trim()) next.baseUrl = '请输入 Base URL'
    if (!form.modelName.trim()) next.modelName = '请输入模型名'
    if (!editing && !form.apiKey) next.apiKey = '请输入 API Key'
    if (Object.keys(next).length > 0) {
      setErrors(next)
      return
    }
    const payload: ModelConfigPayload = {
      name: form.name.trim(),
      providerType: form.providerType,
      baseUrl: form.baseUrl.trim(),
      modelName: form.modelName.trim(),
      type: form.type,
      enabled: form.enabled,
      ...(form.apiKey ? { apiKey: form.apiKey } : {}),
    }
    setSaving(true)
    try {
      if (editing) {
        await adminApi.updateModelConfig(editing.id, payload)
        toast.success('模型配置已更新')
      } else {
        await adminApi.createModelConfig(payload)
        toast.success('模型配置已创建')
      }
      setModalOpen(false)
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  const handleSetDefault = async (record: ModelConfig) => {
    try {
      await adminApi.setDefaultModelConfig(record.id)
      toast.success(`已将「${record.name}」设为默认模型`)
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, '设置默认失败'))
    }
  }

  const handleToggle = async (record: ModelConfig, enabled: boolean) => {
    setConfigs((prev) => prev.map((c) => (c.id === record.id ? { ...c, enabled } : c)))
    try {
      await adminApi.updateModelConfig(record.id, toPayload(record, { enabled }))
    } catch (error) {
      setConfigs((prev) => prev.map((c) => (c.id === record.id ? { ...c, enabled: !enabled } : c)))
      toast.error(extractErrorMessage(error, '操作失败'))
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) return
    try {
      await adminApi.deleteModelConfig(deleteTarget.id)
      toast.success('已删除')
      setDeleteTarget(null)
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, '删除失败'))
    }
  }

  return (
    <div>
      <PageHeader
        title="模型配置"
        description="接入对话与向量模型，标星的配置将作为平台默认模型"
        extra={
          <Button onClick={openCreate}>
            <Plus className="size-4" />
            新建模型配置
          </Button>
        }
      />

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
      ) : configs.length === 0 ? (
        <div className="flex flex-col items-center gap-4 py-16 text-center">
          <p className="text-sm text-muted-foreground">
            还没有模型配置，接入第一个模型后即可开始对话
          </p>
          <Button onClick={openCreate}>
            <Plus className="size-4" />
            新建模型配置
          </Button>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[44px]"> </TableHead>
              <TableHead>名称</TableHead>
              <TableHead className="w-[130px]">提供商</TableHead>
              <TableHead className="w-[110px]">类型</TableHead>
              <TableHead>模型名</TableHead>
              <TableHead className="w-[140px]">API Key</TableHead>
              <TableHead className="w-[80px]">启用</TableHead>
              <TableHead className="w-[100px]">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {configs.map((record) => (
              <TableRow key={record.id}>
                <TableCell>
                  {record.isDefault ? (
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <span>
                          <Star className="size-4 fill-[var(--ax-star)] text-[var(--ax-star)]" />
                        </span>
                      </TooltipTrigger>
                      <TooltipContent>默认模型</TooltipContent>
                    </Tooltip>
                  ) : (
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="size-7 text-muted-foreground"
                          onClick={() => void handleSetDefault(record)}
                        >
                          <Star className="size-4" />
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent>设为默认</TooltipContent>
                    </Tooltip>
                  )}
                </TableCell>
                <TableCell className="max-w-0 truncate">{record.name}</TableCell>
                <TableCell>
                  <Badge variant="outline">
                    {PROVIDER_OPTIONS.find((o) => o.value === record.providerType)?.label ??
                      record.providerType}
                  </Badge>
                </TableCell>
                <TableCell>
                  <Badge variant={record.type === 'CHAT' ? 'info' : 'default'}>{record.type}</Badge>
                </TableCell>
                <TableCell className="max-w-0 truncate">{record.modelName}</TableCell>
                <TableCell className="font-mono text-xs">{record.maskedApiKey || '—'}</TableCell>
                <TableCell>
                  <Switch
                    checked={record.enabled}
                    onCheckedChange={(checked) => void handleToggle(record, checked)}
                  />
                </TableCell>
                <TableCell>
                  <div className="flex items-center">
                    <Button
                      variant="ghost"
                      size="icon"
                      className="size-7"
                      onClick={() => openEdit(record)}
                    >
                      <Pencil className="size-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="size-7 text-destructive hover:text-destructive"
                      onClick={() => setDeleteTarget(record)}
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
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing ? '编辑模型配置' : '新建模型配置'}</DialogTitle>
          </DialogHeader>
          <div className="mt-1 flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="m-name">名称</Label>
              <Input
                id="m-name"
                placeholder="例如：DeepSeek V3"
                maxLength={60}
                value={form.name}
                onChange={(e) => patch({ name: e.target.value })}
              />
              {errors.name && <p className="text-xs text-destructive">{errors.name}</p>}
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-1.5">
                <Label>提供商</Label>
                <Select
                  value={form.providerType}
                  onValueChange={(v) => patch({ providerType: v as ProviderType })}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {PROVIDER_OPTIONS.map((o) => (
                      <SelectItem key={o.value} value={o.value}>
                        {o.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="flex flex-col gap-1.5">
                <Label>模型类型</Label>
                <Select value={form.type} onValueChange={(v) => patch({ type: v as ModelType })}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {TYPE_OPTIONS.map((o) => (
                      <SelectItem key={o.value} value={o.value}>
                        {o.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="m-baseurl">Base URL</Label>
              <Input
                id="m-baseurl"
                placeholder="https://api.deepseek.com"
                value={form.baseUrl}
                onChange={(e) => patch({ baseUrl: e.target.value })}
              />
              <p className="text-xs text-muted-foreground">服务接口地址，不含具体路径</p>
              {errors.baseUrl && <p className="text-xs text-destructive">{errors.baseUrl}</p>}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="m-modelname">模型名</Label>
              <Input
                id="m-modelname"
                placeholder="deepseek-chat"
                value={form.modelName}
                onChange={(e) => patch({ modelName: e.target.value })}
              />
              {errors.modelName && <p className="text-xs text-destructive">{errors.modelName}</p>}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="m-apikey">API Key</Label>
              <Input
                id="m-apikey"
                type="password"
                placeholder={editing ? '留空保持不变' : 'sk-...'}
                autoComplete="new-password"
                value={form.apiKey}
                onChange={(e) => patch({ apiKey: e.target.value })}
              />
              {editing && (
                <p className="text-xs text-muted-foreground">
                  当前：{editing.maskedApiKey || '未设置'}，留空表示不修改
                </p>
              )}
              {errors.apiKey && <p className="text-xs text-destructive">{errors.apiKey}</p>}
            </div>
            <div className="flex items-center gap-2">
              <Switch
                id="m-enabled"
                checked={form.enabled}
                onCheckedChange={(checked) => patch({ enabled: checked })}
              />
              <Label htmlFor="m-enabled">启用</Label>
            </div>
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
            <AlertDialogTitle>删除模型配置</AlertDialogTitle>
            <AlertDialogDescription>确定删除「{deleteTarget?.name}」吗？</AlertDialogDescription>
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
    </div>
  )
}

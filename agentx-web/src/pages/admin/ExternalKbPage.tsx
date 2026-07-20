import { AlertTriangle, CheckCircle2, Loader2, Pencil, Plus, Trash2, Zap } from 'lucide-react'
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
  DialogDescription,
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
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import * as api from '../../api/externalKb'
import { extractErrorMessage } from '../../api/http'
import ErrorState from '../../components/ErrorState'
import PageHeader from '../../components/PageHeader'
import ExternalKbGuide from './ExternalKbGuide'
import type { ExternalKb, ExternalKbProbe } from '../../types'

interface FormState {
  name: string
  baseUrl: string
  vaultId: string
  topK: number
  similarityThreshold: number
  enabled: boolean
}

const EMPTY: FormState = {
  name: '',
  baseUrl: '',
  vaultId: '',
  topK: 5,
  similarityThreshold: 0.2,
  enabled: true,
}

export default function ExternalKbPage() {
  const [items, setItems] = useState<ExternalKb[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ExternalKb | null>(null)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [errors, setErrors] = useState<Partial<Record<keyof FormState, string>>>({})
  const [probe, setProbe] = useState<ExternalKbProbe | null>(null)
  const [probing, setProbing] = useState(false)
  const [vaults, setVaults] = useState<api.VaultInfo[] | null>(null)
  const [discovering, setDiscovering] = useState(false)
  const [testingId, setTestingId] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<ExternalKb | null>(null)

  const patch = (p: Partial<FormState>) => setForm((prev) => ({ ...prev, ...p }))

  const refresh = async () => {
    try {
      setItems(await api.listExternalKbs())
      setLoadError(null)
    } catch (error) {
      setLoadError(extractErrorMessage(error, '加载外部知识库失败'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refresh()
  }, [])

  const openCreate = () => {
    setEditing(null)
    setForm(EMPTY)
    setErrors({})
    setProbe(null)
    setModalOpen(true)
  }

  const openEdit = (kb: ExternalKb) => {
    setEditing(kb)
    setForm({
      name: kb.name,
      baseUrl: kb.baseUrl,
      vaultId: kb.vaultId,
      topK: kb.topK,
      similarityThreshold: kb.similarityThreshold,
      enabled: kb.enabled,
    })
    setErrors({})
    setProbe(null)
    setModalOpen(true)
  }

  const validate = (): boolean => {
    const next: Partial<Record<keyof FormState, string>> = {}
    if (!form.name.trim()) next.name = '请输入名称'
    if (!form.baseUrl.trim()) next.baseUrl = '请输入服务地址（http://ip:端口）'
    if (!form.vaultId.trim()) next.vaultId = '请输入仓库标识（vault，防多仓库内容互相污染）'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  /** 仓库发现：填了服务地址后一键列出可接入仓库，点选即填 vaultId */
  const doDiscover = async () => {
    if (!form.baseUrl.trim()) {
      setErrors((e) => ({ ...e, baseUrl: '请先填服务地址' }))
      return
    }
    setDiscovering(true)
    setVaults(null)
    try {
      setVaults(await api.discoverVaults(form.baseUrl.trim()))
    } catch (error) {
      toast.error(extractErrorMessage(error, '获取仓库列表失败'))
    } finally {
      setDiscovering(false)
    }
  }

  const doProbe = async () => {
    if (!validate()) return
    setProbing(true)
    setProbe(null)
    try {
      setProbe(await api.probeExternalKb({ ...form }))
    } catch (error) {
      toast.error(extractErrorMessage(error, '探测失败'))
    } finally {
      setProbing(false)
    }
  }

  const handleSave = async () => {
    if (!validate()) return
    setSaving(true)
    try {
      if (editing) {
        await api.updateExternalKb(editing.id, { ...form })
        toast.success('外部知识库已更新')
      } else {
        await api.createExternalKb({ ...form })
        toast.success('外部知识库已接入')
      }
      setModalOpen(false)
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  const handleToggle = async (kb: ExternalKb, enabled: boolean) => {
    setItems((prev) => prev.map((k) => (k.id === kb.id ? { ...k, enabled } : k)))
    try {
      await api.updateExternalKb(kb.id, { ...kb, enabled })
    } catch (error) {
      setItems((prev) => prev.map((k) => (k.id === kb.id ? { ...k, enabled: kb.enabled } : k)))
      toast.error(extractErrorMessage(error, '操作失败'))
    }
  }

  const handleTest = async (kb: ExternalKb) => {
    setTestingId(kb.id)
    try {
      const r = await api.testExternalKb(kb.id)
      if (r.error) toast.error(r.error)
      else if (r.warning) toast.warning(r.warning)
      else toast.success(`连接正常：${r.vaultName ?? kb.vaultId} · ${r.chunkCount} 片段 · ${r.embeddingModel}`)
    } catch (error) {
      toast.error(extractErrorMessage(error, '连接测试失败'))
    } finally {
      setTestingId(null)
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) return
    try {
      await api.deleteExternalKb(deleteTarget.id)
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
        title="外部知识库"
        description="按固定 API 模板（心跳/库信息/文本查询）接入外部知识库，与项目内知识库共存检索；停用即完全忽略。外部库用自己的向量模型检索，无需与本平台模型一致"
        extra={
          <div className="flex items-center gap-2">
            <ExternalKbGuide />
            <Button onClick={openCreate}>
              <Plus className="size-4" />
              接入外部知识库
            </Button>
          </div>
        }
      />

      {loadError ? (
        <ErrorState message={loadError} onRetry={() => { setLoading(true); void refresh() }} />
      ) : loading ? (
        <div className="flex animate-pulse flex-col gap-3 py-6">
          {[0, 1].map((i) => <div key={i} className="h-8 w-full rounded bg-muted" />)}
        </div>
      ) : items.length === 0 ? (
        <div className="flex flex-col items-center gap-4 py-16 text-center">
          <p className="text-sm text-muted-foreground">
            还没有外部知识库。在外部系统（如 Notopolis）按模板实现三个 API 后即可接入
          </p>
          <Button onClick={openCreate}>
            <Plus className="size-4" />
            接入外部知识库
          </Button>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>名称</TableHead>
              <TableHead>服务地址</TableHead>
              <TableHead className="w-[160px]">仓库（vault）</TableHead>
              <TableHead className="w-[80px] text-right">topK</TableHead>
              <TableHead className="w-[80px]">启用</TableHead>
              <TableHead className="w-[130px]">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map((kb) => (
              <TableRow key={kb.id}>
                <TableCell className="max-w-0 truncate">{kb.name}</TableCell>
                <TableCell className="max-w-0 truncate font-mono text-xs">{kb.baseUrl}</TableCell>
                <TableCell className="max-w-0 truncate font-mono text-xs">{kb.vaultId}</TableCell>
                <TableCell className="text-right tabular-nums">{kb.topK}</TableCell>
                <TableCell>
                  <Switch checked={kb.enabled} onCheckedChange={(c) => void handleToggle(kb, c)} />
                </TableCell>
                <TableCell>
                  <div className="flex items-center">
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button variant="ghost" size="icon" className="size-7 text-[var(--ax-ios-orange)] hover:text-[var(--ax-ios-orange)]"
                          disabled={testingId === kb.id} onClick={() => void handleTest(kb)}>
                          {testingId === kb.id
                            ? <Loader2 className="size-4 animate-spin" />
                            : <Zap className="size-4" />}
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent>测试连接（心跳 + 库信息 + 索引状态）</TooltipContent>
                    </Tooltip>
                    <Button variant="ghost" size="icon" className="size-7 text-[var(--ax-ios-blue)] hover:text-[var(--ax-ios-blue)]" onClick={() => openEdit(kb)}>
                      <Pencil className="size-4" />
                    </Button>
                    <Button variant="ghost" size="icon"
                      className="size-7 text-destructive hover:text-destructive"
                      onClick={() => setDeleteTarget(kb)}>
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
            <DialogTitle>{editing ? '编辑外部知识库' : '接入外部知识库'}</DialogTitle>
            <DialogDescription>
              外部系统需实现固定模板的三个 API（心跳/库信息/文本查询）；外部库用自己的向量模型检索，无需与本平台一致
            </DialogDescription>
          </DialogHeader>
          <div className="mt-1 flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="ek-name">名称</Label>
              <Input id="ek-name" placeholder="例如：Obsidian 主库" maxLength={60}
                value={form.name} onChange={(e) => patch({ name: e.target.value })} />
              {errors.name && <p className="text-xs text-destructive">{errors.name}</p>}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="ek-url">服务地址</Label>
              <Input id="ek-url" className="font-mono" placeholder="http://localhost:4777"
                value={form.baseUrl} onChange={(e) => patch({ baseUrl: e.target.value })} />
              {errors.baseUrl && <p className="text-xs text-destructive">{errors.baseUrl}</p>}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="ek-vault">仓库标识（vault）</Label>
              <div className="flex gap-2">
                <Input id="ek-vault" className="font-mono" placeholder="点右侧按钮从服务获取并选择"
                  value={form.vaultId} onChange={(e) => patch({ vaultId: e.target.value })} />
                <Button variant="outline" disabled={discovering} onClick={() => void doDiscover()}>
                  {discovering && <Loader2 className="size-3.5 animate-spin" />}
                  获取仓库列表
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">
                必填：不同仓库内容非同类，混检互相污染
              </p>
              {errors.vaultId && <p className="text-xs text-destructive">{errors.vaultId}</p>}
              {vaults && (
                <div className="mt-1 overflow-hidden rounded-lg border border-border">
                  {vaults.length === 0 ? (
                    <div className="px-3 py-3 text-center text-xs text-muted-foreground">
                      该服务未配置任何仓库
                    </div>
                  ) : (
                    vaults.map((v) => (
                      <button
                        key={v.vaultId}
                        type="button"
                        onClick={() => {
                          patch({ vaultId: v.vaultId, name: form.name || v.name })
                          setErrors((e) => ({ ...e, vaultId: undefined }))
                        }}
                        className={`flex w-full items-center gap-2 border-b border-border/60 px-3 py-2 text-left text-sm transition-colors last:border-0 hover:bg-accent ${
                          form.vaultId === v.vaultId ? 'bg-accent/60' : ''
                        }`}
                      >
                        <span className="min-w-0 flex-1">
                          <span className="block truncate font-medium">{v.name}</span>
                          <span className="block truncate font-mono text-[11px] text-muted-foreground">
                            {v.vaultId} · {v.chunkCount} 片段 · {v.embeddingModel ?? '未建索引'}
                          </span>
                        </span>
                        {form.vaultId === v.vaultId && <CheckCircle2 className="size-4 shrink-0 text-[var(--ax-ok-text)]" />}
                      </button>
                    ))
                  )}
                </div>
              )}
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="ek-topk">召回条数 topK</Label>
                <Input id="ek-topk" type="number" className="rounded-lg" min={1} max={20}
                  value={form.topK}
                  onChange={(e) => patch({ topK: e.target.value === '' ? 5 : Number(e.target.value) })} />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="ek-th">相似度阈值</Label>
                <Input id="ek-th" type="number" className="rounded-lg" min={0} max={1} step={0.05}
                  value={form.similarityThreshold}
                  onChange={(e) =>
                    patch({ similarityThreshold: e.target.value === '' ? 0.2 : Number(e.target.value) })} />
              </div>
            </div>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Switch id="ek-enabled" checked={form.enabled}
                  onCheckedChange={(c) => patch({ enabled: c })} />
                <Label htmlFor="ek-enabled">启用（停用即完全忽略该库）</Label>
              </div>
              <Button variant="outline" size="sm" disabled={probing} onClick={() => void doProbe()}>
                {probing ? <Loader2 className="size-3.5 animate-spin" /> : <Zap className="size-3.5" />}
                测试连接
              </Button>
            </div>
            {probe && (
              <div className="rounded-lg bg-muted/40 px-3 py-2 text-xs">
                {probe.error ? (
                  <span className="text-destructive">{probe.error}</span>
                ) : (
                  <div className="flex flex-col gap-1">
                    <span className="inline-flex items-center gap-1.5 text-[var(--ax-ok-text)]">
                      <CheckCircle2 className="size-3.5" />
                      连接正常 · {probe.vaultName ?? form.vaultId} · {probe.chunkCount} 片段 ·
                      模型 {probe.embeddingModel ?? '未知'}（{probe.dims} 维）
                    </span>
                    {probe.warning && (
                      <span className="inline-flex items-center gap-1.5 text-warning">
                        <AlertTriangle className="size-3.5 shrink-0" />
                        {probe.warning}
                      </span>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setModalOpen(false)}>取消</Button>
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
            <AlertDialogTitle>删除外部知识库</AlertDialogTitle>
            <AlertDialogDescription>
              确定删除「{deleteTarget?.name}」吗？仅解除接入，不影响外部系统数据。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => void handleDelete()}>
              删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}

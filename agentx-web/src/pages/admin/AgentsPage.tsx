import { Loader2, Pencil, Plus, Trash2 } from 'lucide-react'
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
import { MultiSelect } from '@/components/ui/multi-select'
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
import { Textarea } from '@/components/ui/textarea'
import * as adminApi from '../../api/admin'
import * as agentsApi from '../../api/agents'
import { extractErrorMessage } from '../../api/http'
import * as kbApi from '../../api/kb'
import ErrorState from '../../components/ErrorState'
import PageHeader from '../../components/PageHeader'
import type { AgentPayload, AgentView, KnowledgeBase, ToolView, WorkflowType } from '../../types'

const WORKFLOW_OPTIONS: { value: WorkflowType; label: string }[] = [
  { value: 'REACT', label: 'ReAct（推理 + 工具循环）' },
  { value: 'CHAIN', label: 'Chain（提示链）' },
  { value: 'ROUTING', label: 'Routing（路由分发）' },
  { value: 'PARALLELIZATION', label: 'Parallelization（并行聚合）' },
  { value: 'ORCHESTRATOR_WORKERS', label: 'Orchestrator-Workers（编排）' },
  { value: 'EVALUATOR_OPTIMIZER', label: 'Evaluator-Optimizer（评审优化）' },
]

/** 后端 View 的 toolNames/kbIds 是 JSON 字符串，安全解析为 string[] */
function parseJsonArray(raw: string | null): string[] {
  if (!raw) return []
  try {
    const parsed: unknown = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter((v): v is string => typeof v === 'string') : []
  } catch {
    return []
  }
}

interface AgentForm {
  name: string
  description: string
  systemPrompt: string
  workflowType: WorkflowType
  toolNames: string[]
  kbIds: string[]
  maxIterations: number
  enabled: boolean
}

const EMPTY_FORM: AgentForm = {
  name: '',
  description: '',
  systemPrompt: '',
  workflowType: 'REACT',
  toolNames: [],
  kbIds: [],
  maxIterations: 10,
  enabled: true,
}

function toPayload(form: AgentForm): AgentPayload {
  return {
    name: form.name.trim(),
    ...(form.description.trim() ? { description: form.description.trim() } : {}),
    ...(form.systemPrompt.trim() ? { systemPrompt: form.systemPrompt.trim() } : {}),
    workflowType: form.workflowType,
    toolNames: form.toolNames,
    kbIds: form.kbIds,
    maxIterations: form.maxIterations,
    enabled: form.enabled,
  }
}

export default function AgentsPage() {
  const [agents, setAgents] = useState<AgentView[]>([])
  const [tools, setTools] = useState<ToolView[]>([])
  const [kbs, setKbs] = useState<KnowledgeBase[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<AgentView | null>(null)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState<AgentForm>(EMPTY_FORM)
  const [nameError, setNameError] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<AgentView | null>(null)

  const patch = (p: Partial<AgentForm>) => setForm((prev) => ({ ...prev, ...p }))

  const refresh = async () => {
    try {
      setAgents(await adminApi.listAdminAgents())
      setLoadError(null)
    } catch (error) {
      setLoadError(extractErrorMessage(error, '加载 Agent 列表失败'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refresh()
    // 表单多选数据源：工具目录 + 知识库（失败不阻塞列表）
    void agentsApi.listTools().then(setTools).catch(() => setTools([]))
    void kbApi.listKbs().then(setKbs).catch(() => setKbs([]))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const kbNameById = new Map(kbs.map((kb) => [kb.id, kb.name]))

  const openCreate = () => {
    setEditing(null)
    setForm(EMPTY_FORM)
    setNameError(null)
    setModalOpen(true)
  }

  const openEdit = (agent: AgentView) => {
    setEditing(agent)
    setForm({
      name: agent.name,
      description: agent.description ?? '',
      systemPrompt: agent.systemPrompt ?? '',
      workflowType: agent.workflowType,
      toolNames: parseJsonArray(agent.toolNames),
      kbIds: parseJsonArray(agent.kbIds),
      maxIterations: agent.maxIterations,
      enabled: agent.enabled,
    })
    setNameError(null)
    setModalOpen(true)
  }

  const handleSave = async () => {
    if (!form.name.trim()) {
      setNameError('请输入名称')
      return
    }
    setSaving(true)
    try {
      if (editing) {
        await adminApi.updateAgent(editing.id, toPayload(form))
        toast.success('Agent 已更新')
      } else {
        await adminApi.createAgent(toPayload(form))
        toast.success('Agent 已创建')
      }
      setModalOpen(false)
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  /** 启停：PUT 全量更新（toolNames/kbIds 需先 parse 回数组） */
  const handleToggle = async (agent: AgentView, enabled: boolean) => {
    setAgents((prev) => prev.map((a) => (a.id === agent.id ? { ...a, enabled } : a)))
    try {
      await adminApi.updateAgent(agent.id, {
        name: agent.name,
        ...(agent.description ? { description: agent.description } : {}),
        ...(agent.systemPrompt ? { systemPrompt: agent.systemPrompt } : {}),
        workflowType: agent.workflowType,
        toolNames: parseJsonArray(agent.toolNames),
        kbIds: parseJsonArray(agent.kbIds),
        maxIterations: agent.maxIterations,
        enabled,
      })
    } catch (error) {
      setAgents((prev) => prev.map((a) => (a.id === agent.id ? { ...a, enabled: !enabled } : a)))
      toast.error(extractErrorMessage(error, '操作失败'))
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) return
    try {
      await adminApi.deleteAgent(deleteTarget.id)
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
        title="Agent"
        description="编排工作流、工具与知识库，构建面向业务场景的智能体"
        extra={
          <Button onClick={openCreate}>
            <Plus className="size-4" />
            新建 Agent
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
      ) : agents.length === 0 ? (
        <div className="flex flex-col items-center gap-4 py-16 text-center">
          <p className="text-sm text-muted-foreground">还没有 Agent，创建第一个智能体开始编排</p>
          <Button onClick={openCreate}>
            <Plus className="size-4" />
            新建 Agent
          </Button>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>名称</TableHead>
              <TableHead className="w-[150px]">工作流</TableHead>
              <TableHead>工具</TableHead>
              <TableHead>知识库</TableHead>
              <TableHead className="w-[90px] text-right">最大迭代</TableHead>
              <TableHead className="w-[80px]">启用</TableHead>
              <TableHead className="w-[100px]">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {agents.map((agent) => {
              const toolNames = parseJsonArray(agent.toolNames)
              const kbIds = parseJsonArray(agent.kbIds)
              return (
                <TableRow key={agent.id}>
                  <TableCell className="max-w-0 truncate">{agent.name}</TableCell>
                  <TableCell>
                    <Badge variant="info">{agent.workflowType}</Badge>
                  </TableCell>
                  <TableCell className="max-w-0">
                    {toolNames.length === 0 ? (
                      '—'
                    ) : (
                      <div className="flex flex-wrap gap-1">
                        {toolNames.map((n) => (
                          <Badge key={n} variant="outline" className="font-mono text-[11px]">
                            {n}
                          </Badge>
                        ))}
                      </div>
                    )}
                  </TableCell>
                  <TableCell className="max-w-0">
                    {kbIds.length === 0 ? (
                      '—'
                    ) : (
                      <div className="flex flex-wrap gap-1">
                        {kbIds.map((id) => (
                          <Badge key={id} variant="outline">
                            {kbNameById.get(id) ?? id}
                          </Badge>
                        ))}
                      </div>
                    )}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{agent.maxIterations}</TableCell>
                  <TableCell>
                    <Switch
                      checked={agent.enabled}
                      onCheckedChange={(checked) => void handleToggle(agent, checked)}
                    />
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center">
                      <Button
                        variant="ghost"
                        size="icon"
                        className="size-7"
                        onClick={() => openEdit(agent)}
                      >
                        <Pencil className="size-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="size-7 text-destructive hover:text-destructive"
                        onClick={() => setDeleteTarget(agent)}
                      >
                        <Trash2 className="size-4" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      )}

      <Dialog open={modalOpen} onOpenChange={setModalOpen}>
        <DialogContent className="max-h-[85vh] max-w-[640px] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editing ? '编辑 Agent' : '新建 Agent'}</DialogTitle>
          </DialogHeader>
          <div className="mt-1 flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="a-name">名称</Label>
              <Input
                id="a-name"
                placeholder="例如：数据分析助手"
                maxLength={60}
                value={form.name}
                onChange={(e) => patch({ name: e.target.value })}
              />
              {nameError && <p className="text-xs text-destructive">{nameError}</p>}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="a-desc">描述</Label>
              <Input
                id="a-desc"
                placeholder="这个 Agent 擅长什么？（可选）"
                maxLength={200}
                value={form.description}
                onChange={(e) => patch({ description: e.target.value })}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="a-prompt">系统提示词</Label>
              <Textarea
                id="a-prompt"
                rows={5}
                className="rounded-lg"
                placeholder="定义 Agent 的角色、能力边界与回复风格…"
                value={form.systemPrompt}
                onChange={(e) => patch({ systemPrompt: e.target.value })}
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-1.5">
                <Label>工作流类型</Label>
                <Select
                  value={form.workflowType}
                  onValueChange={(v) => patch({ workflowType: v as WorkflowType })}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {WORKFLOW_OPTIONS.map((o) => (
                      <SelectItem key={o.value} value={o.value}>
                        {o.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="a-maxiter">最大迭代次数</Label>
                <Input
                  id="a-maxiter"
                  type="number"
                  className="rounded-lg"
                  min={1}
                  max={50}
                  value={Number.isFinite(form.maxIterations) ? form.maxIterations : ''}
                  onChange={(e) =>
                    patch({ maxIterations: e.target.value === '' ? NaN : Number(e.target.value) })
                  }
                />
                <p className="text-xs text-muted-foreground">
                  工作流内推理与工具调用的最大循环轮数
                </p>
              </div>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>可用工具</Label>
              <MultiSelect
                options={tools.map((t) => ({
                  value: t.name,
                  label: t.description ? `${t.name} — ${t.description}` : t.name,
                }))}
                value={form.toolNames}
                onChange={(v) => patch({ toolNames: v })}
                placeholder="选择该 Agent 可调用的工具"
                renderTag={(v) => v}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>关联知识库</Label>
              <MultiSelect
                options={kbs.map((kb) => ({ value: kb.id, label: kb.name }))}
                value={form.kbIds}
                onChange={(v) => patch({ kbIds: v })}
                placeholder="选择该 Agent 可检索的知识库"
              />
            </div>
            <div className="flex items-center gap-2">
              <Switch
                id="a-enabled"
                checked={form.enabled}
                onCheckedChange={(checked) => patch({ enabled: checked })}
              />
              <Label htmlFor="a-enabled">启用</Label>
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
            <AlertDialogTitle>删除 Agent</AlertDialogTitle>
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

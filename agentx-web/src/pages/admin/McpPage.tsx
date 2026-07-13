import { Loader2, Pencil, Plug, Plus, Trash2, Zap } from 'lucide-react'
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
import { Textarea } from '@/components/ui/textarea'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import * as adminApi from '../../api/admin'
import { extractErrorMessage } from '../../api/http'
import ErrorState from '../../components/ErrorState'
import PageHeader from '../../components/PageHeader'
import type { McpServer, McpToolPreview, McpTransport } from '../../types'

/** 弹窗表单值（connectParams 拆成友好字段，提交时再组装 JSON） */
interface McpFormValues {
  name: string
  transport: McpTransport
  enabled: boolean
  command: string
  /** 每行一个参数 */
  args: string
  /** 每行一条 KEY=VALUE */
  env: string
  url: string
  /** 每行一条 Key: Value */
  headers: string
}

const EMPTY_FORM: McpFormValues = {
  name: '',
  transport: 'STDIO',
  enabled: true,
  command: '',
  args: '',
  env: '',
  url: '',
  headers: '',
}

function parseLines(text: string | undefined): string[] {
  return (text ?? '')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
}

function parseKvLines(text: string | undefined, sep: string): Record<string, string> {
  const result: Record<string, string> = {}
  for (const line of parseLines(text)) {
    const idx = line.indexOf(sep)
    if (idx <= 0) continue
    result[line.slice(0, idx).trim()] = line.slice(idx + sep.length).trim()
  }
  return result
}

function buildConnectParams(values: McpFormValues): string {
  if (values.transport === 'STDIO') {
    return JSON.stringify({
      command: values.command ?? '',
      args: parseLines(values.args),
      env: parseKvLines(values.env, '='),
    })
  }
  return JSON.stringify({
    url: values.url ?? '',
    headers: parseKvLines(values.headers, ':'),
  })
}

/** 回显：connectParams JSON → 表单友好字段 */
function connectParamsToFields(server: McpServer): Partial<McpFormValues> {
  if (!server.connectParams) return {}
  try {
    const parsed = JSON.parse(server.connectParams) as Record<string, unknown>
    if (server.transport === 'STDIO') {
      const args = Array.isArray(parsed.args) ? (parsed.args as unknown[]) : []
      const env = (parsed.env ?? {}) as Record<string, unknown>
      return {
        command: typeof parsed.command === 'string' ? parsed.command : '',
        args: args.map(String).join('\n'),
        env: Object.entries(env)
          .map(([k, v]) => `${k}=${String(v)}`)
          .join('\n'),
      }
    }
    const headers = (parsed.headers ?? {}) as Record<string, unknown>
    return {
      url: typeof parsed.url === 'string' ? parsed.url : '',
      headers: Object.entries(headers)
        .map(([k, v]) => `${k}: ${String(v)}`)
        .join('\n'),
    }
  } catch {
    return {}
  }
}

export default function McpPage() {
  const [servers, setServers] = useState<McpServer[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<McpServer | null>(null)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState<McpFormValues>(EMPTY_FORM)
  const [errors, setErrors] = useState<Partial<Record<keyof McpFormValues, string>>>({})
  const [testingId, setTestingId] = useState<string | null>(null)
  const [testResult, setTestResult] = useState<{ name: string; tools: McpToolPreview[] } | null>(
    null,
  )
  const [deleteTarget, setDeleteTarget] = useState<McpServer | null>(null)

  const patch = (p: Partial<McpFormValues>) => setForm((prev) => ({ ...prev, ...p }))

  const refresh = async () => {
    try {
      setServers(await adminApi.listMcpServers())
      setLoadError(null)
    } catch (error) {
      setLoadError(extractErrorMessage(error, '加载 MCP 服务失败'))
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

  const openEdit = (server: McpServer) => {
    setEditing(server)
    setForm({ ...EMPTY_FORM, name: server.name, transport: server.transport, enabled: server.enabled, ...connectParamsToFields(server) })
    setErrors({})
    setModalOpen(true)
  }

  const handleSave = async () => {
    const next: Partial<Record<keyof McpFormValues, string>> = {}
    if (!form.name.trim()) next.name = '请输入名称'
    if (form.transport === 'STREAMABLE_HTTP') {
      if (!form.url.trim()) next.url = '请输入服务 URL'
    } else if (!form.command.trim()) {
      next.command = '请输入启动命令'
    }
    if (Object.keys(next).length > 0) {
      setErrors(next)
      return
    }
    const payload: adminApi.McpServerPayload = {
      name: form.name.trim(),
      transport: form.transport,
      connectParams: buildConnectParams(form),
      enabled: form.enabled,
    }
    setSaving(true)
    try {
      if (editing) {
        await adminApi.updateMcpServer(editing.id, payload)
        toast.success('MCP 服务已更新')
      } else {
        await adminApi.createMcpServer(payload)
        toast.success('MCP 服务已创建')
      }
      setModalOpen(false)
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  /** 启停：后端只有 PUT 全量更新，透传原 connectParams */
  const handleToggle = async (server: McpServer, enabled: boolean) => {
    setServers((prev) => prev.map((s) => (s.id === server.id ? { ...s, enabled } : s)))
    try {
      await adminApi.updateMcpServer(server.id, {
        name: server.name,
        transport: server.transport,
        connectParams: server.connectParams ?? '{}',
        enabled,
      })
    } catch (error) {
      setServers((prev) => prev.map((s) => (s.id === server.id ? { ...s, enabled: !enabled } : s)))
      toast.error(extractErrorMessage(error, '操作失败'))
    }
  }

  const handleTest = async (server: McpServer) => {
    setTestingId(server.id)
    try {
      const tools = await adminApi.testMcpConnection(server.id)
      setTestResult({ name: server.name, tools })
    } catch (error) {
      toast.error(extractErrorMessage(error, '连接测试失败'))
    } finally {
      setTestingId(null)
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) return
    try {
      await adminApi.deleteMcpServer(deleteTarget.id)
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
        title="MCP 服务"
        description="接入 Model Context Protocol 服务，为 Agent 扩展远程工具能力"
        extra={
          <Button onClick={openCreate}>
            <Plus className="size-4" />
            新建 MCP 服务
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
      ) : servers.length === 0 ? (
        <div className="flex flex-col items-center gap-4 py-16 text-center">
          <p className="text-sm text-muted-foreground">还没有 MCP 服务，接入一个为 Agent 扩展工具</p>
          <Button onClick={openCreate}>
            <Plus className="size-4" />
            新建 MCP 服务
          </Button>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>名称</TableHead>
              <TableHead className="w-[170px]">传输方式</TableHead>
              <TableHead>连接参数</TableHead>
              <TableHead className="w-[80px]">启用</TableHead>
              <TableHead className="w-[150px]">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {servers.map((record) => (
              <TableRow key={record.id}>
                <TableCell className="max-w-0 truncate">{record.name}</TableCell>
                <TableCell>
                  <Badge variant={record.transport === 'STDIO' ? 'info' : 'default'}>
                    {record.transport}
                  </Badge>
                </TableCell>
                <TableCell className="max-w-0 truncate font-mono text-xs">
                  {record.connectParams || '—'}
                </TableCell>
                <TableCell>
                  <Switch
                    checked={record.enabled}
                    onCheckedChange={(checked) => void handleToggle(record, checked)}
                  />
                </TableCell>
                <TableCell>
                  <div className="flex items-center">
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="size-7"
                          disabled={testingId === record.id}
                          onClick={() => void handleTest(record)}
                        >
                          {testingId === record.id ? (
                            <Loader2 className="size-4 animate-spin" />
                          ) : (
                            <Zap className="size-4" />
                          )}
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent>测试连接</TooltipContent>
                    </Tooltip>
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
            <DialogTitle>{editing ? '编辑 MCP 服务' : '新建 MCP 服务'}</DialogTitle>
          </DialogHeader>
          <div className="mt-1 flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="mcp-name">名称</Label>
              <Input
                id="mcp-name"
                placeholder="例如：filesystem"
                maxLength={60}
                value={form.name}
                onChange={(e) => patch({ name: e.target.value })}
              />
              {errors.name && <p className="text-xs text-destructive">{errors.name}</p>}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>传输方式</Label>
              <Select
                value={form.transport}
                onValueChange={(v) => patch({ transport: v as McpTransport })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="STDIO">STDIO（本地进程）</SelectItem>
                  <SelectItem value="STREAMABLE_HTTP">Streamable HTTP（远程服务）</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {form.transport === 'STREAMABLE_HTTP' ? (
              <>
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="mcp-url">URL</Label>
                  <Input
                    id="mcp-url"
                    placeholder="https://mcp.example.com/mcp"
                    value={form.url}
                    onChange={(e) => patch({ url: e.target.value })}
                  />
                  {errors.url && <p className="text-xs text-destructive">{errors.url}</p>}
                </div>
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="mcp-headers">请求头（每行一条，格式 Key: Value）</Label>
                  <Textarea
                    id="mcp-headers"
                    rows={3}
                    className="rounded-lg font-mono text-xs"
                    placeholder="Authorization: Bearer xxx"
                    value={form.headers}
                    onChange={(e) => patch({ headers: e.target.value })}
                  />
                </div>
              </>
            ) : (
              <>
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="mcp-command">启动命令</Label>
                  <Input
                    id="mcp-command"
                    placeholder="npx"
                    value={form.command}
                    onChange={(e) => patch({ command: e.target.value })}
                  />
                  {errors.command && <p className="text-xs text-destructive">{errors.command}</p>}
                </div>
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="mcp-args">参数（每行一个）</Label>
                  <Textarea
                    id="mcp-args"
                    rows={3}
                    className="rounded-lg font-mono text-xs"
                    placeholder={'-y\n@modelcontextprotocol/server-filesystem\n/data'}
                    value={form.args}
                    onChange={(e) => patch({ args: e.target.value })}
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="mcp-env">环境变量（每行一条，格式 KEY=VALUE）</Label>
                  <Textarea
                    id="mcp-env"
                    rows={2}
                    className="rounded-lg font-mono text-xs"
                    placeholder="API_TOKEN=xxx"
                    value={form.env}
                    onChange={(e) => patch({ env: e.target.value })}
                  />
                </div>
              </>
            )}

            <div className="flex items-center gap-2">
              <Switch
                id="mcp-enabled"
                checked={form.enabled}
                onCheckedChange={(checked) => patch({ enabled: checked })}
              />
              <Label htmlFor="mcp-enabled">启用</Label>
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

      <Dialog open={testResult !== null} onOpenChange={(o) => !o && setTestResult(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{testResult ? `连接成功 · ${testResult.name}` : '连接结果'}</DialogTitle>
          </DialogHeader>
          <div className="max-h-[50vh] overflow-y-auto">
            {(testResult?.tools ?? []).length === 0 ? (
              <p className="py-8 text-center text-sm text-muted-foreground">
                连接成功，但该服务未暴露任何工具
              </p>
            ) : (
              <ul className="flex flex-col gap-1">
                {testResult?.tools.map((tool) => (
                  <li key={tool.name} className="flex gap-3 rounded-lg px-2 py-2.5 hover:bg-accent/50">
                    <Plug className="mt-0.5 size-4 shrink-0 text-primary" />
                    <div className="min-w-0">
                      <div className="font-mono text-[13px]">{tool.name}</div>
                      <div className="text-xs text-muted-foreground">
                        {tool.description || '无描述'}
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <DialogFooter>
            <Button onClick={() => setTestResult(null)}>关闭</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={deleteTarget !== null} onOpenChange={(o) => !o && setDeleteTarget(null)}>
        <AlertDialogContent className="max-w-md">
          <AlertDialogHeader>
            <AlertDialogTitle>删除 MCP 服务</AlertDialogTitle>
            <AlertDialogDescription>
              确定删除「{deleteTarget?.name}」吗？其提供的远程工具将不可用。
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
    </div>
  )
}

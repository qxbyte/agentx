import { FileText } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
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
import * as agentsApi from '../../api/agents'
import { extractErrorMessage } from '../../api/http'
import ErrorState from '../../components/ErrorState'
import PageHeader from '../../components/PageHeader'
import type { ToolView } from '../../types'

/** paramsSchema 可能是 JSON 字符串或对象，统一美化输出 */
function formatSchema(schema: ToolView['paramsSchema']): string {
  if (schema === null || schema === undefined || schema === '') return '（无参数定义）'
  if (typeof schema === 'string') {
    try {
      return JSON.stringify(JSON.parse(schema), null, 2)
    } catch {
      return schema
    }
  }
  return JSON.stringify(schema, null, 2)
}

export default function ToolsPage() {
  const [tools, setTools] = useState<ToolView[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [schemaTool, setSchemaTool] = useState<ToolView | null>(null)

  const refresh = useCallback(() => {
    agentsApi
      .listTools()
      .then((list) => {
        setTools(list)
        setLoadError(null)
      })
      .catch((error: unknown) => setLoadError(extractErrorMessage(error, '加载工具目录失败')))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const handleToggle = async (tool: ToolView, enabled: boolean) => {
    setTools((prev) => prev.map((t) => (t.name === tool.name ? { ...t, enabled } : t)))
    try {
      await adminApi.toggleToolEnabled(tool.name, enabled)
    } catch (error) {
      setTools((prev) => prev.map((t) => (t.name === tool.name ? { ...t, enabled: !enabled } : t)))
      toast.error(extractErrorMessage(error, '操作失败'))
    }
  }

  return (
    <div>
      <PageHeader
        title="工具目录"
        description="平台内置与 MCP 服务提供的全部工具，停用后 Agent 将无法调用"
      />

      {loadError ? (
        <ErrorState
          message={loadError}
          onRetry={() => {
            setLoading(true)
            refresh()
          }}
        />
      ) : loading ? (
        <div className="flex animate-pulse flex-col gap-3 py-6">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-8 w-full rounded bg-muted" />
          ))}
        </div>
      ) : tools.length === 0 ? (
        <div className="py-16 text-center text-sm text-muted-foreground">
          暂无可用工具，接入 MCP 服务后这里会自动出现其提供的工具
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[220px]">工具名</TableHead>
              <TableHead className="w-[90px]">来源</TableHead>
              <TableHead>描述</TableHead>
              <TableHead className="w-[80px]">参数</TableHead>
              <TableHead className="w-[80px]">启用</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {tools.map((tool) => (
              <TableRow key={tool.name}>
                <TableCell className="font-mono text-xs font-semibold">{tool.name}</TableCell>
                <TableCell>
                  <Badge variant={tool.source === 'CODE' ? 'info' : 'default'}>{tool.source}</Badge>
                </TableCell>
                <TableCell className="max-w-0 truncate text-muted-foreground">
                  {tool.description}
                </TableCell>
                <TableCell>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="size-7 text-[var(--ax-ios-blue)] hover:text-[var(--ax-ios-blue)]"
                        onClick={() => setSchemaTool(tool)}
                      >
                        <FileText className="size-4" />
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent>查看参数 Schema</TooltipContent>
                  </Tooltip>
                </TableCell>
                <TableCell>
                  <Switch
                    checked={tool.enabled}
                    onCheckedChange={(checked) => void handleToggle(tool, checked)}
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <Sheet open={schemaTool !== null} onOpenChange={(o) => !o && setSchemaTool(null)}>
        <SheetContent side="right" className="flex w-full flex-col gap-0 p-0 sm:max-w-[520px]">
          <SheetHeader className="border-b border-border">
            <SheetTitle>
              {schemaTool ? `参数 Schema · ${schemaTool.name}` : '参数 Schema'}
            </SheetTitle>
          </SheetHeader>
          <div className="ax-scroll flex-1 overflow-y-auto p-4">
            {schemaTool && (
              <>
                <p className="mt-0 text-sm text-muted-foreground">
                  {schemaTool.description || '无描述'}
                </p>
                <pre className="ax-toolcall-pre mt-3" style={{ maxHeight: 'none' }}>
                  {formatSchema(schemaTool.paramsSchema)}
                </pre>
              </>
            )}
          </div>
        </SheetContent>
      </Sheet>
    </div>
  )
}

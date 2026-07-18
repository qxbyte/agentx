import { Blocks, Download, Loader2, Plus, RefreshCw, Trash2 } from 'lucide-react'
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
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { extractErrorMessage } from '../../api/http'
import * as pluginsApi from '../../api/plugins'
import AppShell from '../../components/AppShell'
import { useChatStore } from '../../stores/chat'
import type { InstalledPluginView, MarketplaceView } from '../../types'

/** 插件管理:marketplace 添加 + 安装/启停/卸载(本机目录化,对齐 Claude Code) */
export default function PluginsPage() {
  const [marketplaces, setMarketplaces] = useState<MarketplaceView[]>([])
  const [installed, setInstalled] = useState<InstalledPluginView[]>([])
  const [loading, setLoading] = useState(true)
  const [source, setSource] = useState('')
  const [adding, setAdding] = useState(false)
  const [installing, setInstalling] = useState<string | null>(null)
  const [updating, setUpdating] = useState<string | null>(null)
  const [removeTarget, setRemoveTarget] = useState<
    { kind: 'marketplace'; name: string } | { kind: 'plugin'; id: string; name: string } | null
  >(null)

  /** 变更后刷新聊天输入框的 / 补全菜单缓存 */
  const refreshMenu = () => void useChatStore.getState().loadSkills()

  const refresh = async () => {
    try {
      const [mps, plugins] = await Promise.all([
        pluginsApi.listMarketplaces(),
        pluginsApi.listPlugins(),
      ])
      setMarketplaces(mps)
      setInstalled(plugins)
    } catch (error) {
      toast.error(extractErrorMessage(error, '加载插件信息失败'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refresh()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleAdd = async () => {
    const value = source.trim()
    if (!value) return
    setAdding(true)
    try {
      const mp = await pluginsApi.addMarketplace(value)
      toast.success(`已添加 marketplace「${mp.name}」（${mp.plugins.length} 个插件可安装）`)
      setSource('')
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, '添加失败'))
    } finally {
      setAdding(false)
    }
  }

  const handleInstall = async (name: string, marketplace: string) => {
    setInstalling(`${name}@${marketplace}`)
    try {
      const plugin = await pluginsApi.installPlugin(name, marketplace)
      toast.success(
        `已安装 ${plugin.name} v${plugin.version}，${plugin.skillCount} 个技能进入 / 菜单`,
      )
      refreshMenu()
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, '安装失败'))
    } finally {
      setInstalling(null)
    }
  }

  const handleUpdatePlugin = async (id: string) => {
    setUpdating(id)
    try {
      const plugin = await pluginsApi.updatePlugin(id)
      toast.success(`已更新 ${plugin.name} 至 v${plugin.version}`)
      refreshMenu()
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, '更新失败'))
    } finally {
      setUpdating(null)
    }
  }

  const handleUpdateMarketplace = async (name: string) => {
    setUpdating(`mp:${name}`)
    try {
      await pluginsApi.updateMarketplace(name)
      toast.success(`marketplace「${name}」已更新`)
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, '更新失败'))
    } finally {
      setUpdating(null)
    }
  }

  const handleToggle = async (plugin: InstalledPluginView, enabled: boolean) => {
    setInstalled((prev) => prev.map((p) => (p.id === plugin.id ? { ...p, enabled } : p)))
    try {
      await pluginsApi.setPluginEnabled(plugin.id, enabled)
      refreshMenu()
    } catch (error) {
      setInstalled((prev) =>
        prev.map((p) => (p.id === plugin.id ? { ...p, enabled: !enabled } : p)),
      )
      toast.error(extractErrorMessage(error, '操作失败'))
    }
  }

  const handleRemove = async () => {
    if (!removeTarget) return
    try {
      if (removeTarget.kind === 'marketplace') {
        await pluginsApi.removeMarketplace(removeTarget.name)
        toast.success('已移除 marketplace')
      } else {
        await pluginsApi.uninstallPlugin(removeTarget.id)
        toast.success('已卸载插件')
      }
      setRemoveTarget(null)
      refreshMenu()
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, '操作失败'))
    }
  }

  return (
    <AppShell title="插件">
      {/* 添加 marketplace */}
      <div className="mb-6 flex items-center gap-2">
        <Input
          placeholder="owner/repo、git URL 或本地绝对路径（如 obra/superpowers）"
          value={source}
          onChange={(e) => setSource(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && void handleAdd()}
          className="max-w-[480px]"
        />
        <Button onClick={() => void handleAdd()} disabled={adding || !source.trim()}>
          {adding ? <Loader2 className="size-4 animate-spin" /> : <Plus className="size-4" />}
          添加 Marketplace
        </Button>
      </div>

      {loading ? (
        <div className="flex animate-pulse flex-col gap-3 py-6">
          {[0, 1].map((i) => (
            <div key={i} className="h-24 w-full rounded-xl bg-muted" />
          ))}
        </div>
      ) : marketplaces.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-16 text-center">
          <Blocks className="size-8 text-muted-foreground" />
          <p className="max-w-md text-sm text-muted-foreground">
            插件是可安装的技能包（兼容 Claude Code 插件格式）。添加一个 marketplace
            开始——比如 obra/superpowers
          </p>
        </div>
      ) : (
        <div className="flex flex-col gap-6">
          {marketplaces.map((mp) => (
            <div key={mp.name} className="rounded-xl border border-[var(--ax-border)]">
              <div className="flex items-center gap-2 border-b border-[var(--ax-border)] px-4 py-2.5">
                <span className="font-medium">{mp.name}</span>
                <span className="truncate text-xs text-muted-foreground">{mp.locator}</span>
                <Badge variant="outline" className="ml-auto shrink-0">
                  {mp.plugins.length} 个插件
                </Badge>
                <Button
                  variant="ghost"
                  size="icon"
                  className="size-7 shrink-0"
                  title="更新 marketplace（git 拉取最新清单）"
                  disabled={updating === `mp:${mp.name}`}
                  onClick={() => void handleUpdateMarketplace(mp.name)}
                >
                  <RefreshCw className={`size-4 ${updating === `mp:${mp.name}` ? 'animate-spin' : ''}`} />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  className="size-7 shrink-0 text-destructive hover:text-destructive"
                  onClick={() => setRemoveTarget({ kind: 'marketplace', name: mp.name })}
                >
                  <Trash2 className="size-4" />
                </Button>
              </div>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-[180px]">插件</TableHead>
                    <TableHead>描述</TableHead>
                    <TableHead className="w-[90px]">版本</TableHead>
                    <TableHead className="w-[220px]">状态 / 操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {mp.plugins.map((p) => {
                    const id = `${p.name}@${mp.name}`
                    const inst = installed.find((i) => i.id === id)
                    return (
                      <TableRow key={p.name}>
                        <TableCell className="font-mono text-[13px] font-medium">
                          {p.name}
                        </TableCell>
                        <TableCell className="max-w-0 truncate text-muted-foreground">
                          {p.description}
                        </TableCell>
                        <TableCell className="font-mono text-xs">
                          {(inst?.version ?? p.version) || '—'}
                        </TableCell>
                        <TableCell>
                          {inst ? (
                            <div className="flex items-center gap-2">
                              <Switch
                                checked={inst.enabled}
                                onCheckedChange={(checked) => void handleToggle(inst, checked)}
                              />
                              <span className="shrink-0 text-xs text-muted-foreground">
                                {inst.skillCount} 技能
                                {inst.agentCount > 0 ? ` · ${inst.agentCount} 子代理` : ''}
                                {inst.mcpCount > 0 ? ` · ${inst.mcpCount} MCP(默认停用)` : ''}
                              </span>
                              {inst.unsupported.length > 0 && (
                                <Badge
                                  variant="outline"
                                  className="cursor-help text-[10px] text-muted-foreground"
                                  title={`该插件还捆绑了 ${inst.unsupported.join('、')} 能力，AgentX 暂不加载这部分（技能不受影响）`}
                                >
                                  含 {inst.unsupported.join('/')}（暂不支持）
                                </Badge>
                              )}
                              <Button
                                variant="ghost"
                                size="icon"
                                className="size-7"
                                title="更新插件（重新拉取并安装最新版本）"
                                disabled={updating === id}
                                onClick={() => void handleUpdatePlugin(id)}
                              >
                                <RefreshCw className={`size-4 ${updating === id ? 'animate-spin' : ''}`} />
                              </Button>
                              <Button
                                variant="ghost"
                                size="icon"
                                className="size-7 text-destructive hover:text-destructive"
                                onClick={() =>
                                  setRemoveTarget({ kind: 'plugin', id, name: p.name })
                                }
                              >
                                <Trash2 className="size-4" />
                              </Button>
                            </div>
                          ) : (
                            <Button
                              size="sm"
                              variant="outline"
                              disabled={installing === id}
                              onClick={() => void handleInstall(p.name, mp.name)}
                            >
                              {installing === id ? (
                                <Loader2 className="size-3.5 animate-spin" />
                              ) : (
                                <Download className="size-3.5" />
                              )}
                              安装
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </div>
          ))}
        </div>
      )}

      <AlertDialog open={removeTarget !== null} onOpenChange={(o) => !o && setRemoveTarget(null)}>
        <AlertDialogContent className="max-w-md">
          <AlertDialogHeader>
            <AlertDialogTitle>
              {removeTarget?.kind === 'marketplace' ? '移除 Marketplace' : '卸载插件'}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {removeTarget?.kind === 'marketplace'
                ? `确定移除「${removeTarget.name}」吗？已安装的插件不受影响。`
                : `确定卸载「${removeTarget?.name}」吗？其提供的技能将从 / 菜单消失。`}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => void handleRemove()}
            >
              确定
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </AppShell>
  )
}

import { Loader2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { extractErrorMessage } from '../../api/http'
import * as proxyApi from '../../api/proxy'
import ErrorState from '../../components/ErrorState'
import PageHeader from '../../components/PageHeader'

export default function ProxyPage() {
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [enabled, setEnabled] = useState(false)
  const [host, setHost] = useState('')
  const [port, setPort] = useState('')
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)

  const refresh = async () => {
    try {
      const c = await proxyApi.getProxy()
      setEnabled(c.enabled)
      setHost(c.host ?? '')
      setPort(c.port != null ? String(c.port) : '')
      setLoadError(null)
    } catch (error) {
      setLoadError(extractErrorMessage(error, '加载代理配置失败'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refresh()
  }, [])

  const buildConfig = (): proxyApi.ProxyConfig => ({
    enabled,
    host: host.trim(),
    port: port.trim() === '' ? null : Number(port.trim()),
  })

  const handleSave = async () => {
    if (enabled && (host.trim() === '' || port.trim() === '' || !Number.isFinite(Number(port)))) {
      toast.error('启用代理时必须填写有效的主机与端口')
      return
    }
    setSaving(true)
    try {
      const saved = await proxyApi.updateProxy(buildConfig())
      setEnabled(saved.enabled)
      setHost(saved.host ?? '')
      setPort(saved.port != null ? String(saved.port) : '')
      toast.success('代理配置已保存，即时生效')
    } catch (error) {
      toast.error(extractErrorMessage(error, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  const handleTest = async () => {
    setTesting(true)
    try {
      const r = await proxyApi.testProxy()
      if (r.ok) {
        toast.success('联网测试通过（已能访问外网）')
      } else {
        toast.error(`联网测试失败：${r.error ?? '未知错误'}`)
      }
    } catch (error) {
      toast.error(extractErrorMessage(error, '测试失败'))
    } finally {
      setTesting(false)
    }
  }

  return (
    <div>
      <PageHeader
        title="网络代理"
        description="配置联网工具（webFetch / webSearch）的出网代理。仅在此显式启用后才走代理；不启用则一律直连，不受系统代理影响。"
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
            <div key={i} className="h-10 w-full max-w-md rounded bg-muted" />
          ))}
        </div>
      ) : (
        <div className="flex max-w-md flex-col gap-5">
          <div className="flex items-center justify-between">
            <div className="flex flex-col gap-0.5">
              <Label htmlFor="proxy-enabled">启用代理</Label>
              <span className="text-xs text-muted-foreground">
                关闭时联网工具直连；开启并填写主机端口后立即生效
              </span>
            </div>
            <Switch id="proxy-enabled" checked={enabled} onCheckedChange={setEnabled} />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="proxy-host">代理主机</Label>
            <Input
              id="proxy-host"
              placeholder="例如 127.0.0.1"
              value={host}
              disabled={!enabled}
              onChange={(e) => setHost(e.target.value)}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="proxy-port">代理端口</Label>
            <Input
              id="proxy-port"
              type="number"
              placeholder="例如 7890（Clash HTTP 代理端口）"
              value={port}
              disabled={!enabled}
              onChange={(e) => setPort(e.target.value)}
            />
            <span className="text-xs text-muted-foreground">
              填本地代理客户端的 HTTP 代理端口（Clash 默认 7890）；仅支持 HTTP 代理
            </span>
          </div>

          <div className="flex items-center gap-2">
            <Button onClick={() => void handleSave()} disabled={saving}>
              {saving && <Loader2 className="size-4 animate-spin" />}
              保存
            </Button>
            <Button variant="outline" onClick={() => void handleTest()} disabled={testing}>
              {testing && <Loader2 className="size-4 animate-spin" />}
              测试联网
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}

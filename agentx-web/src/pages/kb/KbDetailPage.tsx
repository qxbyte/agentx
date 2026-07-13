import { ArrowLeft } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { extractErrorMessage } from '../../api/http'
import * as kbApi from '../../api/kb'
import AppShell from '../../components/AppShell'
import type { KnowledgeBase } from '../../types'
import DocumentsTab from './DocumentsTab'
import HitTestTab from './HitTestTab'
import SettingsTab from './SettingsTab'

export default function KbDetailPage() {
  const { kbId } = useParams<{ kbId: string }>()
  const navigate = useNavigate()

  const [kb, setKb] = useState<KnowledgeBase | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!kbId) return
    setLoading(true)
    kbApi
      .listKbs()
      .then((kbs) => {
        const found = kbs.find((item) => item.id === kbId) ?? null
        setKb(found)
        if (!found) {
          toast.error('知识库不存在或已删除')
          navigate('/kb', { replace: true })
        }
      })
      .catch((error: unknown) => toast.error(extractErrorMessage(error, '加载知识库失败')))
      .finally(() => setLoading(false))
  }, [kbId, navigate])

  const title = (
    <span className="ax-topbar-back">
      <button
        type="button"
        className="ax-icon-btn"
        aria-label="返回知识库列表"
        onClick={() => navigate('/kb')}
      >
        <ArrowLeft className="size-4" />
      </button>
      {kb?.name ?? '知识库详情'}
    </span>
  )

  return (
    <AppShell title={title}>
      {loading || !kb || !kbId ? (
        <div className="flex animate-pulse flex-col gap-6" aria-busy="true">
          <div className="h-5 w-56 rounded bg-muted" />
          <div className="flex flex-col gap-3">
            {[0, 1, 2, 3, 4].map((i) => (
              <div key={i} className="h-3 w-full rounded bg-muted" />
            ))}
          </div>
        </div>
      ) : (
        <Tabs defaultValue="documents">
          <TabsList>
            <TabsTrigger value="documents">文档</TabsTrigger>
            <TabsTrigger value="hit-test">命中测试</TabsTrigger>
            <TabsTrigger value="settings">设置</TabsTrigger>
          </TabsList>
          <TabsContent value="documents">
            <DocumentsTab kbId={kbId} />
          </TabsContent>
          <TabsContent value="hit-test">
            <HitTestTab kb={kb} />
          </TabsContent>
          <TabsContent value="settings">
            <SettingsTab kb={kb} onUpdated={setKb} />
          </TabsContent>
        </Tabs>
      )}
    </AppShell>
  )
}

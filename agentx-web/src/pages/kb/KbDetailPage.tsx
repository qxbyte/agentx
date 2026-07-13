import { ArrowLeftOutlined } from '@ant-design/icons'
import { App as AntdApp, Skeleton, Tabs } from 'antd'
import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
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
  const { message } = AntdApp.useApp()

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
          message.error('知识库不存在或已删除')
          navigate('/kb', { replace: true })
        }
      })
      .catch((error: unknown) => message.error(extractErrorMessage(error, '加载知识库失败')))
      .finally(() => setLoading(false))
  }, [kbId, message, navigate])

  const title = (
    <span className="ax-topbar-back">
      <button
        type="button"
        className="ax-icon-btn"
        aria-label="返回知识库列表"
        onClick={() => navigate('/kb')}
      >
        <ArrowLeftOutlined />
      </button>
      {kb?.name ?? '知识库详情'}
    </span>
  )

  return (
    <AppShell title={title}>
      {loading || !kb || !kbId ? (
        <div aria-busy="true">
          <Skeleton active title={{ width: 220 }} paragraph={false} style={{ marginBottom: 24 }} />
          <Skeleton active paragraph={{ rows: 5 }} />
        </div>
      ) : (
        <Tabs
          defaultActiveKey="documents"
          items={[
            {
              key: 'documents',
              label: '文档',
              children: <DocumentsTab kbId={kbId} />,
            },
            {
              key: 'hit-test',
              label: '命中测试',
              children: <HitTestTab kb={kb} />,
            },
            {
              key: 'settings',
              label: '设置',
              children: <SettingsTab kb={kb} onUpdated={setKb} />,
            },
          ]}
        />
      )}
    </AppShell>
  )
}

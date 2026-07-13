import { BookOutlined, DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import { App as AntdApp, Button, Empty, Form, Modal, Skeleton } from 'antd'
import { useEffect, useState } from 'react'
import type { MouseEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { extractErrorMessage } from '../../api/http'
import * as kbApi from '../../api/kb'
import AppShell from '../../components/AppShell'
import ErrorState from '../../components/ErrorState'
import type { KbPayload, KnowledgeBase } from '../../types'
import KbConfigFormItems from './KbConfigFormItems'

const KB_FORM_DEFAULTS: Partial<KbPayload> = {
  chunkSize: 1000,
  chunkOverlap: 200,
  topK: 5,
  similarityThreshold: 0.2,
}

export default function KbListPage() {
  const navigate = useNavigate()
  const { message, modal } = AntdApp.useApp()
  const [form] = Form.useForm<KbPayload>()

  const [kbs, setKbs] = useState<KnowledgeBase[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<KnowledgeBase | null>(null)
  const [saving, setSaving] = useState(false)

  const refresh = async () => {
    try {
      setKbs(await kbApi.listKbs())
      setLoadError(null)
    } catch (error) {
      setLoadError(extractErrorMessage(error, '加载知识库失败'))
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
    form.resetFields()
    form.setFieldsValue(KB_FORM_DEFAULTS)
    setModalOpen(true)
  }

  const openEdit = (kb: KnowledgeBase, event: MouseEvent) => {
    event.stopPropagation()
    setEditing(kb)
    form.resetFields()
    form.setFieldsValue({
      name: kb.name,
      description: kb.description ?? undefined,
      chunkSize: kb.chunkSize,
      chunkOverlap: kb.chunkOverlap,
      topK: kb.topK,
      similarityThreshold: kb.similarityThreshold,
    })
    setModalOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      if (editing) {
        await kbApi.updateKb(editing.id, values)
        message.success('知识库已更新')
      } else {
        await kbApi.createKb(values)
        message.success('知识库已创建')
      }
      setModalOpen(false)
      await refresh()
    } catch (error) {
      message.error(extractErrorMessage(error, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  const confirmDelete = (kb: KnowledgeBase, event: MouseEvent) => {
    event.stopPropagation()
    modal.confirm({
      title: '删除知识库',
      content: `确定删除「${kb.name}」吗？其中的文档与分段将一并删除，不可恢复。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      async onOk() {
        try {
          await kbApi.deleteKb(kb.id)
          message.success('已删除')
          await refresh()
        } catch (error) {
          message.error(extractErrorMessage(error, '删除失败'))
        }
      },
    })
  }

  return (
    <AppShell
      title="知识库"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建知识库
        </Button>
      }
    >
      {loading ? (
        <div className="ax-kb-grid" aria-busy="true">
          {[0, 1, 2].map((i) => (
            <div key={i} className="ax-kb-card" style={{ cursor: 'default' }}>
              <Skeleton active title={{ width: '55%' }} paragraph={{ rows: 2 }} />
            </div>
          ))}
        </div>
      ) : loadError ? (
        <ErrorState
          message={loadError}
          onRetry={() => {
            setLoading(true)
            void refresh()
          }}
        />
      ) : kbs.length === 0 ? (
        <Empty className="ax-page-empty" description="还没有知识库，创建第一个开始沉淀团队知识">
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建知识库
          </Button>
        </Empty>
      ) : (
        <div className="ax-kb-grid">
          {kbs.map((kb) => (
            <div
              key={kb.id}
              className="ax-kb-card"
              role="button"
              tabIndex={0}
              onClick={() => navigate(`/kb/${kb.id}`)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') navigate(`/kb/${kb.id}`)
              }}
            >
              <div className="ax-kb-card-head">
                <span className="ax-kb-card-icon">
                  <BookOutlined />
                </span>
                <span className="ax-kb-card-name">{kb.name}</span>
              </div>
              <p className="ax-kb-card-desc">{kb.description || '暂无描述'}</p>
              <div className="ax-kb-card-meta">
                <span>{kb.createdAt ? `创建于 ${kb.createdAt.slice(0, 10)}` : ''}</span>
                <span className="ax-kb-card-actions" onClick={(e) => e.stopPropagation()}>
                  <Button
                    type="text"
                    size="small"
                    icon={<EditOutlined />}
                    aria-label="编辑知识库"
                    onClick={(e) => openEdit(kb, e)}
                  />
                  <Button
                    type="text"
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                    aria-label="删除知识库"
                    onClick={(e) => confirmDelete(kb, e)}
                  />
                </span>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal
        title={editing ? '编辑知识库' : '新建知识库'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => void handleSave()}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
          <KbConfigFormItems />
        </Form>
      </Modal>
    </AppShell>
  )
}

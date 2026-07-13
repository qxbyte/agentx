import { DeleteOutlined, InboxOutlined, RedoOutlined } from '@ant-design/icons'
import { App as AntdApp, Button, Empty, Progress, Table, Tag, Tooltip, Upload } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { extractErrorMessage } from '../../api/http'
import * as kbApi from '../../api/kb'
import type { DocStatus, DocView, IngestTask } from '../../types'
import SegmentsDrawer from './SegmentsDrawer'

const STATUS_META: Record<DocStatus, { color: string; label: string }> = {
  UPLOADED: { color: 'default', label: '已上传' },
  PARSING: { color: 'processing', label: '解析中' },
  INGESTING: { color: 'processing', label: '入库中' },
  READY: { color: 'success', label: '就绪' },
  FAILED: { color: 'error', label: '失败' },
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const POLLING_STATUSES: DocStatus[] = ['PARSING', 'INGESTING']

export default function DocumentsTab({ kbId }: { kbId: string }) {
  const { message, modal } = AntdApp.useApp()

  const [docs, setDocs] = useState<DocView[]>([])
  const [loading, setLoading] = useState(true)
  /** docId -> 最近一次任务状态（进度 / errorMsg） */
  const [tasks, setTasks] = useState<Record<string, IngestTask>>({})
  const [activeDoc, setActiveDoc] = useState<DocView | null>(null)

  const refresh = useCallback(async () => {
    try {
      setDocs(await kbApi.listDocuments(kbId))
    } catch (error) {
      message.error(extractErrorMessage(error, '加载文档列表失败'))
    } finally {
      setLoading(false)
    }
  }, [kbId, message])

  useEffect(() => {
    void refresh()
  }, [refresh])

  /** 需要每 2s 轮询任务进度的文档 */
  const pollingIds = useMemo(
    () => docs.filter((d) => POLLING_STATUSES.includes(d.status)).map((d) => d.id),
    [docs],
  )
  const pollingKey = pollingIds.join(',')

  useEffect(() => {
    if (pollingIds.length === 0) return
    const timer = setInterval(() => {
      void (async () => {
        const entries = await Promise.all(
          pollingIds.map(async (id) => {
            try {
              return [id, await kbApi.fetchDocTask(id)] as const
            } catch {
              return [id, null] as const
            }
          }),
        )
        const valid = entries.filter((e): e is readonly [string, IngestTask] => e[1] !== null)
        if (valid.length > 0) {
          setTasks((prev) => ({ ...prev, ...Object.fromEntries(valid) }))
        }
        // 有任务终结时刷新列表，让状态列与分段数落到最终值
        if (valid.some(([, t]) => t.status === 'SUCCEEDED' || t.status === 'FAILED')) {
          void refresh()
        }
      })()
    }, 2000)
    return () => clearInterval(timer)
    // pollingKey 代表 pollingIds 内容，避免数组引用变化导致重建定时器
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pollingKey, refresh])

  /** FAILED 文档补拉一次任务详情以展示 errorMsg */
  useEffect(() => {
    const failedWithoutTask = docs.filter((d) => d.status === 'FAILED' && !tasks[d.id])
    if (failedWithoutTask.length === 0) return
    void Promise.all(
      failedWithoutTask.map(async (d) => {
        try {
          const task = await kbApi.fetchDocTask(d.id)
          setTasks((prev) => ({ ...prev, [d.id]: task }))
        } catch {
          /* 任务详情拉取失败时静默，表格仍显示 FAILED 状态 */
        }
      }),
    )
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [docs])

  const handleReingest = async (doc: DocView) => {
    try {
      await kbApi.reingestDocument(doc.id)
      message.success('已重新发起入库')
      setTasks((prev) => {
        const next = { ...prev }
        delete next[doc.id]
        return next
      })
      await refresh()
    } catch (error) {
      message.error(extractErrorMessage(error, '重试失败'))
    }
  }

  const confirmDelete = (doc: DocView) => {
    modal.confirm({
      title: '删除文档',
      content: `确定删除「${doc.filename}」及其全部分段吗？`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      async onOk() {
        try {
          await kbApi.deleteDocument(doc.id)
          message.success('已删除')
          await refresh()
        } catch (error) {
          message.error(extractErrorMessage(error, '删除失败'))
        }
      },
    })
  }

  const columns: ColumnsType<DocView> = [
    {
      title: '文件名',
      dataIndex: 'filename',
      ellipsis: true,
    },
    {
      title: '大小',
      dataIndex: 'sizeBytes',
      width: 100,
      render: (v: number) => formatBytes(v),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 220,
      render: (status: DocStatus, doc) => {
        const meta = STATUS_META[status]
        const task = tasks[doc.id]
        if (POLLING_STATUSES.includes(status)) {
          return (
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
              <Tag color={meta.color} style={{ marginRight: 0 }}>
                {meta.label}
              </Tag>
              <Progress
                percent={task?.progress ?? 0}
                size="small"
                style={{ width: 110, marginBottom: 0 }}
              />
            </span>
          )
        }
        if (status === 'FAILED') {
          return (
            <Tooltip title={task?.errorMsg || '入库失败'}>
              <Tag color={meta.color}>{meta.label}</Tag>
            </Tooltip>
          )
        }
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    {
      title: '分段数',
      dataIndex: 'segmentCount',
      width: 90,
      align: 'right',
    },
    {
      title: '操作',
      key: 'actions',
      width: 110,
      render: (_, doc) => (
        <span onClick={(e) => e.stopPropagation()}>
          {doc.status === 'FAILED' && (
            <Tooltip title="重新入库">
              <Button
                type="text"
                size="small"
                icon={<RedoOutlined />}
                onClick={() => void handleReingest(doc)}
              />
            </Tooltip>
          )}
          <Tooltip title="删除">
            <Button
              type="text"
              size="small"
              danger
              icon={<DeleteOutlined />}
              onClick={() => confirmDelete(doc)}
            />
          </Tooltip>
        </span>
      ),
    },
  ]

  return (
    <div>
      <Upload.Dragger
        multiple
        showUploadList={false}
        customRequest={({ file, onSuccess, onError }) => {
          kbApi
            .uploadDocument(kbId, file as File)
            .then(() => {
              onSuccess?.(undefined)
              message.success(`「${(file as File).name}」上传成功，开始解析`)
              void refresh()
            })
            .catch((error: unknown) => {
              onError?.(error as Error)
              message.error(extractErrorMessage(error, '上传失败'))
            })
        }}
        style={{ marginBottom: 16 }}
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">点击或拖拽文件到此处上传</p>
        <p className="ant-upload-hint">上传后自动解析并向量化入库，支持批量上传</p>
      </Upload.Dragger>

      <Table<DocView>
        rowKey="id"
        columns={columns}
        dataSource={docs}
        loading={loading}
        pagination={false}
        size="middle"
        scroll={{ x: 640 }}
        onRow={(doc) => ({
          onClick: () => setActiveDoc(doc),
          style: { cursor: 'pointer' },
        })}
        locale={{
          emptyText: loading ? (
            ' '
          ) : (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              style={{ padding: '24px 0' }}
              description="还没有文档，拖拽文件到上方区域即可入库"
            />
          ),
        }}
      />

      <SegmentsDrawer doc={activeDoc} onClose={() => setActiveDoc(null)} />
    </div>
  )
}

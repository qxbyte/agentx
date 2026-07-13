import { App as AntdApp, Button, Drawer, Empty, Input, Skeleton, Switch, Tag } from 'antd'
import { useEffect, useState } from 'react'
import { extractErrorMessage } from '../../api/http'
import * as kbApi from '../../api/kb'
import type { DocView, SegmentView } from '../../types'

interface SegmentsDrawerProps {
  doc: DocView | null
  onClose: () => void
}

/** 文档分段列表：内容可编辑（PUT）、启停（PATCH enabled） */
export default function SegmentsDrawer({ doc, onClose }: SegmentsDrawerProps) {
  const { message } = AntdApp.useApp()

  const [segments, setSegments] = useState<SegmentView[]>([])
  const [loading, setLoading] = useState(false)
  /** segmentId -> 编辑中的草稿内容（undefined 表示未修改） */
  const [drafts, setDrafts] = useState<Record<string, string>>({})
  const [savingId, setSavingId] = useState<string | null>(null)

  useEffect(() => {
    if (!doc) return
    setSegments([])
    setDrafts({})
    setLoading(true)
    kbApi
      .listSegments(doc.id)
      .then(setSegments)
      .catch((error: unknown) => message.error(extractErrorMessage(error, '加载分段失败')))
      .finally(() => setLoading(false))
  }, [doc, message])

  const handleSave = async (segment: SegmentView) => {
    const content = drafts[segment.id]
    if (content === undefined) return
    if (!content.trim()) {
      message.warning('分段内容不能为空')
      return
    }
    setSavingId(segment.id)
    try {
      const updated = await kbApi.updateSegment(segment.id, content)
      setSegments((prev) => prev.map((s) => (s.id === segment.id ? { ...s, ...updated } : s)))
      setDrafts((prev) => {
        const next = { ...prev }
        delete next[segment.id]
        return next
      })
      message.success('分段已保存')
    } catch (error) {
      message.error(extractErrorMessage(error, '保存失败'))
    } finally {
      setSavingId(null)
    }
  }

  const handleToggle = async (segment: SegmentView, enabled: boolean) => {
    // 乐观更新，失败回滚
    setSegments((prev) => prev.map((s) => (s.id === segment.id ? { ...s, enabled } : s)))
    try {
      await kbApi.toggleSegmentEnabled(segment.id, enabled)
    } catch (error) {
      setSegments((prev) =>
        prev.map((s) => (s.id === segment.id ? { ...s, enabled: !enabled } : s)),
      )
      message.error(extractErrorMessage(error, '操作失败'))
    }
  }

  return (
    <Drawer
      title={doc ? `分段 · ${doc.filename}` : '分段'}
      width={560}
      open={doc !== null}
      onClose={onClose}
      destroyOnHidden
    >
      {loading ? (
        <div aria-busy="true">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} active title={{ width: '30%' }} paragraph={{ rows: 2 }} style={{ marginBottom: 24 }} />
          ))}
        </div>
      ) : segments.length === 0 ? (
        <Empty description="该文档暂无分段，可能仍在解析或解析失败" />
      ) : (
        segments.map((segment) => {
          const draft = drafts[segment.id]
          const dirty = draft !== undefined && draft !== segment.content
          return (
            <div key={segment.id} className="ax-segment-item">
              <div className="ax-segment-head">
                <span className="ax-segment-seq">#{segment.seqNo}</span>
                <Tag>{segment.charCount} 字符</Tag>
                <span className="ax-segment-actions">
                  {dirty && (
                    <Button
                      type="primary"
                      size="small"
                      loading={savingId === segment.id}
                      onClick={() => void handleSave(segment)}
                    >
                      保存
                    </Button>
                  )}
                  <Switch
                    size="small"
                    checked={segment.enabled}
                    checkedChildren="启用"
                    unCheckedChildren="停用"
                    onChange={(checked) => void handleToggle(segment, checked)}
                  />
                </span>
              </div>
              <Input.TextArea
                value={draft ?? segment.content}
                autoSize={{ minRows: 2, maxRows: 8 }}
                onChange={(e) =>
                  setDrafts((prev) => ({ ...prev, [segment.id]: e.target.value }))
                }
              />
            </div>
          )
        })
      )}
    </Drawer>
  )
}

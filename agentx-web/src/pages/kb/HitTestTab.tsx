import { AimOutlined, SearchOutlined } from '@ant-design/icons'
import { App as AntdApp, Button, Empty, Input, InputNumber, Modal, Progress, Tag, Tooltip } from 'antd'
import { useState } from 'react'
import { extractErrorMessage } from '../../api/http'
import * as kbApi from '../../api/kb'
import type { HitTestResult, KnowledgeBase } from '../../types'

/** score 色阶：高分绿 / 中分品牌蓝紫 / 低分橙 */
function scoreColor(score: number): string {
  if (score >= 0.7) return 'var(--ax-success)'
  if (score >= 0.4) return 'var(--ax-primary)'
  return 'var(--ax-warning)'
}

export default function HitTestTab({ kb }: { kb: KnowledgeBase }) {
  const { message } = AntdApp.useApp()

  const [query, setQuery] = useState('')
  const [topK, setTopK] = useState(kb.topK)
  const [threshold, setThreshold] = useState(kb.similarityThreshold)
  const [testing, setTesting] = useState(false)
  const [hits, setHits] = useState<HitTestResult[] | null>(null)

  const [editingHit, setEditingHit] = useState<HitTestResult | null>(null)
  const [editContent, setEditContent] = useState('')
  const [savingSegment, setSavingSegment] = useState(false)

  const runTest = async () => {
    const q = query.trim()
    if (!q) {
      message.warning('请输入测试查询')
      return
    }
    setTesting(true)
    try {
      setHits(await kbApi.hitTest(kb.id, { query: q, topK, similarityThreshold: threshold }))
    } catch (error) {
      message.error(extractErrorMessage(error, '命中测试失败'))
    } finally {
      setTesting(false)
    }
  }

  const openEdit = (hit: HitTestResult) => {
    setEditingHit(hit)
    setEditContent(hit.content)
  }

  const saveSegment = async () => {
    if (!editingHit) return
    if (!editContent.trim()) {
      message.warning('分段内容不能为空')
      return
    }
    setSavingSegment(true)
    try {
      await kbApi.updateSegment(editingHit.segmentId, editContent)
      setHits(
        (prev) =>
          prev?.map((h) =>
            h.segmentId === editingHit.segmentId ? { ...h, content: editContent } : h,
          ) ?? null,
      )
      setEditingHit(null)
      message.success('分段已保存')
    } catch (error) {
      message.error(extractErrorMessage(error, '保存失败'))
    } finally {
      setSavingSegment(false)
    }
  }

  return (
    <div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'flex-end' }}>
        <div style={{ flex: 1, minWidth: 260 }}>
          <div className="ax-field-label">测试查询</div>
          <Input
            value={query}
            placeholder="输入一个问题，看知识库能召回哪些分段"
            onChange={(e) => setQuery(e.target.value)}
            onPressEnter={() => void runTest()}
            allowClear
          />
        </div>
        <div>
          <div className="ax-field-label">topK</div>
          <InputNumber
            min={1}
            max={20}
            value={topK}
            onChange={(v) => setTopK(v ?? kb.topK)}
            style={{ width: 90 }}
          />
        </div>
        <div>
          <div className="ax-field-label">相似度阈值</div>
          <InputNumber
            min={0}
            max={1}
            step={0.05}
            value={threshold}
            onChange={(v) => setThreshold(v ?? kb.similarityThreshold)}
            style={{ width: 110 }}
          />
        </div>
        <Button
          type="primary"
          icon={<SearchOutlined />}
          loading={testing}
          onClick={() => void runTest()}
        >
          测试
        </Button>
      </div>

      {hits === null && (
        <div className="ax-hit-placeholder">
          <AimOutlined />
          <p>输入一个问题并点击「测试」，检验知识库的召回效果与相似度分布</p>
        </div>
      )}

      {hits !== null &&
        (hits.length === 0 ? (
          <Empty
            style={{ marginTop: 40 }}
            description="没有命中任何分段，试试降低阈值或换个问法"
          />
        ) : (
          <div className="ax-hit-list">
            {hits.map((hit, index) => (
              <div
                key={hit.segmentId}
                className="ax-hit-card"
                role="button"
                tabIndex={0}
                title="点击编辑该分段"
                onClick={() => openEdit(hit)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') openEdit(hit)
                }}
              >
                <div className="ax-hit-card-head">
                  <Tag color="geekblue">#{index + 1}</Tag>
                  <Progress
                    className="ax-hit-card-score"
                    percent={Math.round(hit.score * 100)}
                    size="small"
                    strokeColor={scoreColor(hit.score)}
                    format={(p) => `score ${((p ?? 0) / 100).toFixed(2)}`}
                  />
                  <Tooltip title={hit.docName}>
                    <span
                      style={{
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {hit.docName}
                    </span>
                  </Tooltip>
                </div>
                <p className="ax-hit-card-content">{hit.content}</p>
              </div>
            ))}
          </div>
        ))}

      <Modal
        title="编辑分段"
        open={editingHit !== null}
        onCancel={() => setEditingHit(null)}
        onOk={() => void saveSegment()}
        okText="保存"
        cancelText="取消"
        confirmLoading={savingSegment}
        width={640}
        destroyOnHidden
      >
        <Input.TextArea
          value={editContent}
          autoSize={{ minRows: 6, maxRows: 16 }}
          onChange={(e) => setEditContent(e.target.value)}
        />
      </Modal>
    </div>
  )
}

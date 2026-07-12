import { FileTextOutlined } from '@ant-design/icons'
import { Popover } from 'antd'
import type { RagSource } from '../types'

interface SourceBadgeProps {
  source: RagSource
  index: number
}

/** 单个引用来源角标：点击/悬停弹出片段详情 */
export default function SourceBadge({ source, index }: SourceBadgeProps) {
  const content = (
    <div className="ax-source-pop">
      <div style={{ fontWeight: 600 }}>
        <FileTextOutlined style={{ marginRight: 6 }} />
        {source.docName}
      </div>
      <div style={{ fontSize: 12, color: 'var(--ax-text-faint)', marginTop: 4 }}>
        相关度 {(source.score * 100).toFixed(0)}%
      </div>
      {source.snippet && <div className="ax-source-pop-snippet ax-scroll">{source.snippet}</div>}
    </div>
  )

  return (
    <Popover content={content} placement="top" trigger={['hover', 'click']}>
      <span className="ax-source-badge" role="button" tabIndex={0}>
        <span className="ax-source-index">[{index}]</span>
        <span className="ax-source-name">{source.docName}</span>
      </span>
    </Popover>
  )
}

interface SourceListProps {
  sources: RagSource[]
}

/** 消息底部的引用来源行 */
export function SourceList({ sources }: SourceListProps) {
  if (sources.length === 0) return null
  return (
    <div className="ax-sources">
      <span className="ax-sources-label">引用来源</span>
      {sources.map((source, i) => (
        <SourceBadge key={`${source.segmentId}-${i}`} source={source} index={i + 1} />
      ))}
    </div>
  )
}

import { FileText, FolderOpen, Hash } from 'lucide-react'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import type { RagSource } from '../types'

interface SourceBadgeProps {
  source: RagSource
  index: number
}

/** 来源定位行：文件路径 + 章节链 + 原文行号区间（外部知识库命中携带，无则不渲染） */
function SourceLocation({ source }: { source: RagSource }) {
  const hasLines = source.startLine != null && source.endLine != null
  const section = source.headings?.length ? source.headings.join(' › ') : null
  if (!source.path && !section && !hasLines) return null
  return (
    <div className="mt-1.5 space-y-0.5 text-xs text-muted-foreground">
      {source.path && (
        <div className="flex items-start gap-1.5">
          <FolderOpen className="mt-0.5 size-3 shrink-0" />
          <span className="min-w-0 break-all font-mono text-[11px]">{source.path}</span>
        </div>
      )}
      {(section || hasLines) && (
        <div className="flex items-start gap-1.5">
          <Hash className="mt-0.5 size-3 shrink-0" />
          <span className="min-w-0 break-words">
            {section}
            {section && hasLines && ' · '}
            {hasLines && `第 ${source.startLine}–${source.endLine} 行`}
          </span>
        </div>
      )}
    </div>
  )
}

/** 单个引用来源角标：点击/悬停弹出片段详情 */
export default function SourceBadge({ source, index }: SourceBadgeProps) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <span className="ax-source-badge" role="button" tabIndex={0}>
          <span className="ax-source-index">[{index}]</span>
          <span className="ax-source-name">{source.docName}</span>
        </span>
      </PopoverTrigger>
      <PopoverContent side="top" className="ax-source-pop w-auto max-w-[340px] p-3">
        <div className="flex items-start gap-1.5 font-semibold">
          <FileText className="mt-0.5 size-3.5 shrink-0" />
          <span className="min-w-0 break-all">{source.docName}</span>
        </div>
        <div className="mt-1 text-xs text-muted-foreground">
          相关度 {(source.score * 100).toFixed(0)}%
        </div>
        <SourceLocation source={source} />
        {source.snippet && <div className="ax-source-pop-snippet ax-scroll">{source.snippet}</div>}
      </PopoverContent>
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

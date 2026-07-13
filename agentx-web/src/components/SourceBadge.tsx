import { FileText } from 'lucide-react'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import type { RagSource } from '../types'

interface SourceBadgeProps {
  source: RagSource
  index: number
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
        <div className="flex items-center gap-1.5 font-semibold">
          <FileText className="size-3.5" />
          {source.docName}
        </div>
        <div className="mt-1 text-xs text-muted-foreground">
          相关度 {(source.score * 100).toFixed(0)}%
        </div>
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

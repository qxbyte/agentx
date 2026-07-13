import { Loader2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { extractErrorMessage } from '../../api/http'
import * as kbApi from '../../api/kb'
import type { DocView, SegmentView } from '../../types'

interface SegmentsDrawerProps {
  doc: DocView | null
  onClose: () => void
}

/** 文档分段列表：内容可编辑（PUT）、启停（PATCH enabled） */
export default function SegmentsDrawer({ doc, onClose }: SegmentsDrawerProps) {
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
      .catch((error: unknown) => toast.error(extractErrorMessage(error, '加载分段失败')))
      .finally(() => setLoading(false))
  }, [doc])

  const handleSave = async (segment: SegmentView) => {
    const content = drafts[segment.id]
    if (content === undefined) return
    if (!content.trim()) {
      toast.warning('分段内容不能为空')
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
      toast.success('分段已保存')
    } catch (error) {
      toast.error(extractErrorMessage(error, '保存失败'))
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
      toast.error(extractErrorMessage(error, '操作失败'))
    }
  }

  return (
    <Sheet open={doc !== null} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="right" className="flex w-full flex-col gap-0 p-0 sm:max-w-[560px]">
        <SheetHeader className="border-b border-border">
          <SheetTitle>{doc ? `分段 · ${doc.filename}` : '分段'}</SheetTitle>
        </SheetHeader>
        <div className="ax-scroll flex-1 overflow-y-auto px-4 pb-6">
          {loading ? (
            <div className="flex animate-pulse flex-col gap-6 pt-4">
              {[0, 1, 2].map((i) => (
                <div key={i} className="flex flex-col gap-2">
                  <div className="h-3 w-1/3 rounded bg-muted" />
                  <div className="h-3 w-full rounded bg-muted" />
                  <div className="h-3 w-2/3 rounded bg-muted" />
                </div>
              ))}
            </div>
          ) : segments.length === 0 ? (
            <div className="py-16 text-center text-sm text-muted-foreground">
              该文档暂无分段，可能仍在解析或解析失败
            </div>
          ) : (
            segments.map((segment) => {
              const draft = drafts[segment.id]
              const dirty = draft !== undefined && draft !== segment.content
              return (
                <div key={segment.id} className="ax-segment-item">
                  <div className="ax-segment-head">
                    <span className="ax-segment-seq">#{segment.seqNo}</span>
                    <Badge variant="outline">{segment.charCount} 字符</Badge>
                    <span className="ax-segment-actions">
                      {dirty && (
                        <Button
                          size="sm"
                          disabled={savingId === segment.id}
                          onClick={() => void handleSave(segment)}
                        >
                          {savingId === segment.id && <Loader2 className="size-3.5 animate-spin" />}
                          保存
                        </Button>
                      )}
                      <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                        <Switch
                          checked={segment.enabled}
                          onCheckedChange={(checked) => void handleToggle(segment, checked)}
                        />
                        {segment.enabled ? '启用' : '停用'}
                      </span>
                    </span>
                  </div>
                  <Textarea
                    value={draft ?? segment.content}
                    className="min-h-16 rounded-lg"
                    onChange={(e) =>
                      setDrafts((prev) => ({ ...prev, [segment.id]: e.target.value }))
                    }
                  />
                </div>
              )
            })
          )}
        </div>
      </SheetContent>
    </Sheet>
  )
}

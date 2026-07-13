import { Crosshair, Loader2, Search } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { extractErrorMessage } from '../../api/http'
import * as kbApi from '../../api/kb'
import type { HitTestResult, KnowledgeBase } from '../../types'

/** score 色阶：高分绿 / 中分黑 / 低分暖棕（全部低饱和） */
function scoreColor(score: number): string {
  if (score >= 0.7) return 'var(--ax-success)'
  if (score >= 0.4) return 'var(--ax-primary)'
  return 'var(--ax-warning)'
}

export default function HitTestTab({ kb }: { kb: KnowledgeBase }) {
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
      toast.warning('请输入测试查询')
      return
    }
    setTesting(true)
    try {
      setHits(await kbApi.hitTest(kb.id, { query: q, topK, similarityThreshold: threshold }))
    } catch (error) {
      toast.error(extractErrorMessage(error, '命中测试失败'))
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
      toast.warning('分段内容不能为空')
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
      toast.success('分段已保存')
    } catch (error) {
      toast.error(extractErrorMessage(error, '保存失败'))
    } finally {
      setSavingSegment(false)
    }
  }

  return (
    <div>
      <div className="flex flex-wrap items-end gap-3">
        <div className="min-w-[260px] flex-1">
          <div className="ax-field-label">测试查询</div>
          <Input
            value={query}
            placeholder="输入一个问题，看知识库能召回哪些分段"
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') void runTest()
            }}
          />
        </div>
        <div>
          <div className="ax-field-label">topK</div>
          <Input
            type="number"
            className="w-[90px] rounded-lg"
            min={1}
            max={20}
            value={topK}
            onChange={(e) => setTopK(e.target.value === '' ? kb.topK : Number(e.target.value))}
          />
        </div>
        <div>
          <div className="ax-field-label">相似度阈值</div>
          <Input
            type="number"
            className="w-[110px] rounded-lg"
            min={0}
            max={1}
            step={0.05}
            value={threshold}
            onChange={(e) =>
              setThreshold(e.target.value === '' ? kb.similarityThreshold : Number(e.target.value))
            }
          />
        </div>
        <Button disabled={testing} onClick={() => void runTest()}>
          {testing ? <Loader2 className="size-4 animate-spin" /> : <Search className="size-4" />}
          测试
        </Button>
      </div>

      {hits === null && (
        <div className="ax-hit-placeholder">
          <Crosshair className="size-[26px] opacity-60" />
          <p>输入一个问题并点击「测试」，检验知识库的召回效果与相似度分布</p>
        </div>
      )}

      {hits !== null &&
        (hits.length === 0 ? (
          <div className="mt-10 text-center text-sm text-muted-foreground">
            没有命中任何分段，试试降低阈值或换个问法
          </div>
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
                  <Badge variant="info">#{index + 1}</Badge>
                  <span className="ax-hit-card-score flex items-center gap-2">
                    <span className="inline-block h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
                      <span
                        className="block h-full rounded-full transition-all"
                        style={{
                          width: `${Math.round(hit.score * 100)}%`,
                          background: scoreColor(hit.score),
                        }}
                      />
                    </span>
                    <span className="shrink-0 text-xs text-muted-foreground">
                      score {hit.score.toFixed(2)}
                    </span>
                  </span>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <span className="truncate">{hit.docName}</span>
                    </TooltipTrigger>
                    <TooltipContent>{hit.docName}</TooltipContent>
                  </Tooltip>
                </div>
                <p className="ax-hit-card-content">{hit.content}</p>
              </div>
            ))}
          </div>
        ))}

      <Dialog open={editingHit !== null} onOpenChange={(o) => !o && setEditingHit(null)}>
        <DialogContent className="max-w-[640px]">
          <DialogHeader>
            <DialogTitle>编辑分段</DialogTitle>
          </DialogHeader>
          <Textarea
            value={editContent}
            className="min-h-40 max-h-[60vh]"
            onChange={(e) => setEditContent(e.target.value)}
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditingHit(null)}>
              取消
            </Button>
            <Button onClick={() => void saveSegment()} disabled={savingSegment}>
              {savingSegment && <Loader2 className="size-4 animate-spin" />}
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

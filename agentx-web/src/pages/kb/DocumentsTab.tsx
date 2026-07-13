import { Inbox, RotateCw, Trash2 } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ChangeEvent, DragEvent } from 'react'
import { toast } from 'sonner'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Badge, type BadgeProps } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import { extractErrorMessage } from '../../api/http'
import * as kbApi from '../../api/kb'
import type { DocStatus, DocView, IngestTask } from '../../types'
import SegmentsDrawer from './SegmentsDrawer'

const STATUS_META: Record<DocStatus, { variant: BadgeProps['variant']; label: string }> = {
  UPLOADED: { variant: 'default', label: '已上传' },
  PARSING: { variant: 'info', label: '解析中' },
  INGESTING: { variant: 'info', label: '入库中' },
  READY: { variant: 'success', label: '就绪' },
  FAILED: { variant: 'destructive', label: '失败' },
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

/** 细进度条 */
function Bar({ percent }: { percent: number }) {
  return (
    <span className="inline-block h-1.5 w-[110px] overflow-hidden rounded-full bg-muted align-middle">
      <span
        className="block h-full rounded-full bg-primary transition-all"
        style={{ width: `${Math.max(0, Math.min(100, percent))}%` }}
      />
    </span>
  )
}

const POLLING_STATUSES: DocStatus[] = ['PARSING', 'INGESTING']

export default function DocumentsTab({ kbId }: { kbId: string }) {
  const [docs, setDocs] = useState<DocView[]>([])
  const [loading, setLoading] = useState(true)
  /** docId -> 最近一次任务状态（进度 / errorMsg） */
  const [tasks, setTasks] = useState<Record<string, IngestTask>>({})
  const [activeDoc, setActiveDoc] = useState<DocView | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<DocView | null>(null)
  const [dragging, setDragging] = useState(false)

  const refresh = useCallback(async () => {
    try {
      setDocs(await kbApi.listDocuments(kbId))
    } catch (error) {
      toast.error(extractErrorMessage(error, '加载文档列表失败'))
    } finally {
      setLoading(false)
    }
  }, [kbId])

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
        if (valid.some(([, t]) => t.status === 'SUCCEEDED' || t.status === 'FAILED')) {
          void refresh()
        }
      })()
    }, 2000)
    return () => clearInterval(timer)
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
          /* 静默 */
        }
      }),
    )
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [docs])

  const uploadFiles = (files: File[]) => {
    files.forEach((file) => {
      kbApi
        .uploadDocument(kbId, file)
        .then(() => {
          toast.success(`「${file.name}」上传成功，开始解析`)
          void refresh()
        })
        .catch((error: unknown) => {
          toast.error(extractErrorMessage(error, '上传失败'))
        })
    })
  }

  const handleInputChange = (e: ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) uploadFiles(Array.from(e.target.files))
    e.target.value = ''
  }

  const handleDrop = (e: DragEvent<HTMLLabelElement>) => {
    e.preventDefault()
    setDragging(false)
    if (e.dataTransfer.files) uploadFiles(Array.from(e.dataTransfer.files))
  }

  const handleReingest = async (doc: DocView) => {
    try {
      await kbApi.reingestDocument(doc.id)
      toast.success('已重新发起入库')
      setTasks((prev) => {
        const next = { ...prev }
        delete next[doc.id]
        return next
      })
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, '重试失败'))
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) return
    try {
      await kbApi.deleteDocument(deleteTarget.id)
      toast.success('已删除')
      setDeleteTarget(null)
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, '删除失败'))
    }
  }

  const renderStatus = (doc: DocView) => {
    const meta = STATUS_META[doc.status]
    const task = tasks[doc.id]
    if (POLLING_STATUSES.includes(doc.status)) {
      return (
        <span className="inline-flex items-center gap-2">
          <Badge variant={meta.variant}>{meta.label}</Badge>
          <Bar percent={task?.progress ?? 0} />
        </span>
      )
    }
    if (doc.status === 'FAILED') {
      return (
        <Tooltip>
          <TooltipTrigger asChild>
            <span>
              <Badge variant={meta.variant}>{meta.label}</Badge>
            </span>
          </TooltipTrigger>
          <TooltipContent>{task?.errorMsg || '入库失败'}</TooltipContent>
        </Tooltip>
      )
    }
    return <Badge variant={meta.variant}>{meta.label}</Badge>
  }

  return (
    <div>
      <label
        onDragOver={(e) => {
          e.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={handleDrop}
        className={cn(
          'mb-4 flex cursor-pointer flex-col items-center gap-2 rounded-xl border border-dashed border-input bg-muted/30 px-6 py-8 text-center transition-colors hover:border-muted-foreground',
          dragging && 'border-muted-foreground bg-black/[0.02]',
        )}
      >
        <input type="file" multiple hidden onChange={handleInputChange} />
        <Inbox className="size-7 text-muted-foreground" />
        <p className="text-sm text-foreground">点击或拖拽文件到此处上传</p>
        <p className="text-xs text-muted-foreground">上传后自动解析并向量化入库，支持批量上传</p>
      </label>

      {loading ? (
        <div className="flex animate-pulse flex-col gap-3 py-6">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-8 w-full rounded bg-muted" />
          ))}
        </div>
      ) : docs.length === 0 ? (
        <div className="py-10 text-center text-sm text-muted-foreground">
          还没有文档，拖拽文件到上方区域即可入库
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>文件名</TableHead>
              <TableHead className="w-[100px]">大小</TableHead>
              <TableHead className="w-[220px]">状态</TableHead>
              <TableHead className="w-[90px] text-right">分段数</TableHead>
              <TableHead className="w-[110px]">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {docs.map((doc) => (
              <TableRow
                key={doc.id}
                className="cursor-pointer"
                onClick={() => setActiveDoc(doc)}
              >
                <TableCell className="max-w-0 truncate">{doc.filename}</TableCell>
                <TableCell>{formatBytes(doc.sizeBytes)}</TableCell>
                <TableCell>{renderStatus(doc)}</TableCell>
                <TableCell className="text-right tabular-nums">{doc.segmentCount}</TableCell>
                <TableCell onClick={(e) => e.stopPropagation()}>
                  <div className="flex items-center">
                    {doc.status === 'FAILED' && (
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="size-7"
                            onClick={() => void handleReingest(doc)}
                          >
                            <RotateCw className="size-4" />
                          </Button>
                        </TooltipTrigger>
                        <TooltipContent>重新入库</TooltipContent>
                      </Tooltip>
                    )}
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="size-7 text-destructive hover:text-destructive"
                          onClick={() => setDeleteTarget(doc)}
                        >
                          <Trash2 className="size-4" />
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent>删除</TooltipContent>
                    </Tooltip>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <SegmentsDrawer doc={activeDoc} onClose={() => setActiveDoc(null)} />

      <AlertDialog open={deleteTarget !== null} onOpenChange={(o) => !o && setDeleteTarget(null)}>
        <AlertDialogContent className="max-w-md">
          <AlertDialogHeader>
            <AlertDialogTitle>删除文档</AlertDialogTitle>
            <AlertDialogDescription>
              确定删除「{deleteTarget?.filename}」及其全部分段吗？
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => void handleDelete()}
            >
              删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}

import { ImageOff, Loader2, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { http } from '../api/http'
import Hint from '@/components/ui/hint'

/** 已取回的缩略图 blob URL 缓存（会话级，切消息列表不重复拉取） */
const thumbCache = new Map<string, string>()

/** 全屏图片预览：点遮罩/按 ESC 关闭 */
function Lightbox({ url, filename, onClose }: { url: string; filename: string; onClose: () => void }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return createPortal(
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/80 p-8"
      onClick={onClose}
      role="dialog"
      aria-label={`预览 ${filename}`}
    >
      <img
        src={url}
        alt={filename}
        className="max-h-full max-w-full rounded-lg object-contain shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      />
      <button
        type="button"
        className="absolute right-5 top-5 flex size-9 items-center justify-center rounded-full bg-white/15 text-white transition-colors hover:bg-white/30"
        aria-label="关闭预览"
        onClick={onClose}
      >
        <X className="size-5" />
      </button>
    </div>,
    document.body,
  )
}

/**
 * 图片附件缩略图（方块 + 角标 ×），点击放大预览。
 * 待发送态用本地 previewUrl；历史态经鉴权接口取原图 blob（<img> 无法带 Bearer）。
 */
interface AttachmentThumbProps {
  filename: string
  /** 本地预览（待发送）优先 */
  previewUrl?: string
  /** 历史消息：服务端附件 id，经 /attachments/{id}/raw 取图 */
  attachmentId?: string
  uploading?: boolean
  onRemove?: () => void
}

export default function AttachmentThumb({
  filename,
  previewUrl,
  attachmentId,
  uploading = false,
  onRemove,
}: AttachmentThumbProps) {
  const cached = attachmentId ? thumbCache.get(attachmentId) : undefined
  const [url, setUrl] = useState<string | null>(previewUrl ?? cached ?? null)
  const [failed, setFailed] = useState(false)
  const [previewOpen, setPreviewOpen] = useState(false)

  useEffect(() => {
    if (previewUrl || !attachmentId) return
    const hit = thumbCache.get(attachmentId)
    if (hit) {
      setUrl(hit)
      return
    }
    let alive = true
    http
      .get<Blob>(`/v1/attachments/${attachmentId}/raw`, { responseType: 'blob' })
      .then((res) => {
        const objectUrl = URL.createObjectURL(res.data)
        thumbCache.set(attachmentId, objectUrl)
        if (alive) setUrl(objectUrl)
      })
      .catch(() => {
        if (alive) setFailed(true)
      })
    return () => {
      alive = false
    }
  }, [previewUrl, attachmentId])

  const canPreview = !!url && !failed && !uploading

  return (
    <Hint text={filename}>
    <div
      className={`ax-thumb${canPreview ? ' cursor-zoom-in' : ''}`}
      onClick={() => canPreview && setPreviewOpen(true)}
      role={canPreview ? 'button' : undefined}
      tabIndex={canPreview ? 0 : undefined}
      onKeyDown={(e) => {
        if (e.key === 'Enter' && canPreview) setPreviewOpen(true)
      }}
    >
      {url && !failed ? (
        <img src={url} alt={filename} className="ax-thumb-img" />
      ) : (
        <span className="ax-thumb-placeholder">
          {failed ? <ImageOff className="size-4" /> : <Loader2 className="size-4 animate-spin" />}
        </span>
      )}
      {uploading && (
        <span className="ax-thumb-loading">
          <Loader2 className="size-4 animate-spin" />
        </span>
      )}
      {onRemove && (
        <button
          type="button"
          className="ax-attach-remove"
          aria-label="移除图片"
          onClick={(e) => {
            e.stopPropagation()
            onRemove()
          }}
        >
          <X className="size-3" strokeWidth={3} />
        </button>
      )}
      {previewOpen && url && (
        <Lightbox url={url} filename={filename} onClose={() => setPreviewOpen(false)} />
      )}
    </div>
    </Hint>
  )
}

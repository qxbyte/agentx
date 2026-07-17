import { Download, FileSpreadsheet, FileText, Loader2, Presentation } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { downloadGeneratedFile } from '../api/files'
import { extractErrorMessage } from '../api/http'
import type { ToolCallInfo } from '../types'

/** generateDocument / generateSpreadsheet 的 tool-result JSON（后接可选中文说明后缀） */
interface FileResult {
  fileId: string
  filename: string
  format: string
  sizeBytes: number
  savedPath?: string | null
}

const FORMAT_ICONS: Record<string, typeof FileText> = {
  pptx: Presentation,
  xlsx: FileSpreadsheet,
}

function parseResult(result: unknown): FileResult | null {
  if (typeof result !== 'string' || !result.startsWith('{')) return null
  const end = result.lastIndexOf('}')
  if (end < 0) return null
  try {
    const parsed = JSON.parse(result.slice(0, end + 1)) as FileResult
    return parsed.fileId && parsed.filename ? parsed : null
  } catch {
    return null
  }
}

/** 流式中从工具入参里提前拿文件名（args 为 JSON 字符串） */
function filenameFromArgs(args: unknown): string {
  if (typeof args !== 'string') return ''
  try {
    const parsed = JSON.parse(args) as { filename?: string; format?: string }
    return parsed.filename ?? ''
  } catch {
    return ''
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

interface FileCardProps {
  call: ToolCallInfo
}

/** 生成文件卡片：运行中转圈，完成后展示文件信息 + 下载；失败展示工具返回的原因 */
export default function FileCard({ call }: FileCardProps) {
  const [busy, setBusy] = useState(false)
  const file = parseResult(call.result)
  const running = call.done !== true && call.result === undefined

  const handleDownload = async () => {
    if (!file || busy) return
    setBusy(true)
    try {
      await downloadGeneratedFile(file.fileId, file.filename)
    } catch (error) {
      toast.error(extractErrorMessage(error, '下载失败'))
    } finally {
      setBusy(false)
    }
  }

  if (running) {
    return (
      <div className="ax-filecard ax-filecard--pending">
        <Loader2 className="size-4 animate-spin" />
        <span>正在生成 {filenameFromArgs(call.args) || '文件'}…</span>
      </div>
    )
  }

  if (!file) {
    return (
      <div className="ax-filecard ax-filecard--failed">
        <FileText className="size-4" />
        <span>{typeof call.result === 'string' ? call.result : '文件生成失败'}</span>
      </div>
    )
  }

  const Icon = FORMAT_ICONS[file.format] ?? FileText
  return (
    <button type="button" className="ax-filecard" onClick={() => void handleDownload()}>
      <span className="ax-filecard-icon">
        <Icon className="size-4" />
      </span>
      <span className="ax-filecard-meta">
        <span className="ax-filecard-name">{file.filename}</span>
        <span className="ax-filecard-sub">
          {file.format.toUpperCase()} · {formatSize(file.sizeBytes)}
          {file.savedPath ? ` · 已写入项目 ${file.savedPath}` : ''}
        </span>
      </span>
      <span className="ax-filecard-action">
        {busy ? <Loader2 className="size-4 animate-spin" /> : <Download className="size-4" />}
      </span>
    </button>
  )
}

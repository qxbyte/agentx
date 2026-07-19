import { FileCode, FileSpreadsheet, FileText, Loader2, Presentation, X } from 'lucide-react'
import { formatBytes } from '../lib/attachments'
import Hint from '@/components/ui/hint'

/** 格式 → 图标色块：PDF 红 / Word 蓝 / Excel 绿 / PPT 橙 / 其余灰 */
function formatStyle(filename: string): { color: string; Icon: typeof FileText; label: string } {
  const dot = filename.lastIndexOf('.')
  const ext = dot < 0 ? '' : filename.slice(dot + 1).toLowerCase()
  switch (ext) {
    case 'pdf':
      return { color: '#e2574c', Icon: FileText, label: 'PDF' }
    case 'doc':
    case 'docx':
      return { color: '#2b7cd3', Icon: FileText, label: ext.toUpperCase() }
    case 'xls':
    case 'xlsx':
    case 'csv':
      return { color: '#217346', Icon: FileSpreadsheet, label: ext.toUpperCase() }
    case 'ppt':
    case 'pptx':
      return { color: '#d24726', Icon: Presentation, label: ext.toUpperCase() }
    case 'md':
    case 'markdown':
    case 'txt':
      return { color: '#6e6e80', Icon: FileText, label: ext.toUpperCase() }
    case '':
      return { color: '#6e6e80', Icon: FileText, label: 'FILE' }
    default:
      return { color: '#6e6e80', Icon: FileCode, label: ext.toUpperCase() }
  }
}

interface AttachmentCardProps {
  filename: string
  /** 副标题：默认格式大写；传入则展示自定义（如大小/相对路径/错误原因） */
  subtitle?: string
  uploading?: boolean
  failed?: boolean
  /** 提供时显示右上角 × */
  onRemove?: () => void
  /** 历史气泡内的紧凑只读形态 */
  compact?: boolean
}

/** 附件卡片：图标色块 + 文件名 + 格式副标题 + 悬浮 × */
export default function AttachmentCard({
  filename,
  subtitle,
  uploading = false,
  failed = false,
  onRemove,
  compact = false,
}: AttachmentCardProps) {
  const { color, Icon, label } = formatStyle(filename)
  return (
    <div className={`ax-attach${failed ? ' ax-attach--failed' : ''}${compact ? ' ax-attach--compact' : ''}`}>
      <span className="ax-attach-icon" style={{ background: failed ? 'var(--ax-danger)' : color }}>
        {uploading ? <Loader2 className="size-4 animate-spin" /> : <Icon className="size-4" />}
      </span>
      <span className="ax-attach-meta">
        <Hint text={filename}>
          <span className="ax-attach-name">{filename}</span>
        </Hint>
        <span className="ax-attach-sub">{subtitle ?? label}</span>
      </span>
      {onRemove && (
        <button type="button" className="ax-attach-remove" aria-label="移除附件" onClick={onRemove}>
          <X className="size-3" strokeWidth={3} />
        </button>
      )}
    </div>
  )
}

export { formatBytes }

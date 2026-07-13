import { ChevronDown, ChevronRight, Lightbulb, Loader2 } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'

interface ReasoningBlockProps {
  content: string
  /** 思考内容仍在流式输出：自动展开；结束后自动折叠 */
  streaming?: boolean
}

/** 可折叠的「思考过程」浅色块 */
export default function ReasoningBlock({ content, streaming = false }: ReasoningBlockProps) {
  const [open, setOpen] = useState(streaming)
  const wasStreaming = useRef(streaming)

  useEffect(() => {
    if (streaming) {
      setOpen(true)
    } else if (wasStreaming.current) {
      // 思考阶段刚结束：自动折叠
      setOpen(false)
    }
    wasStreaming.current = streaming
  }, [streaming])

  if (!content) return null

  return (
    <div className="ax-reasoning">
      <button
        type="button"
        className="ax-reasoning-header"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
      >
        <Lightbulb className="size-4" />
        <span>{streaming ? '正在思考…' : '思考过程'}</span>
        {streaming && <Loader2 className="size-3.5 animate-spin" />}
        <span className="ml-auto">{open ? <ChevronDown className="size-3.5" /> : <ChevronRight className="size-3.5" />}</span>
      </button>
      {open && <div className="ax-reasoning-content">{content}</div>}
    </div>
  )
}

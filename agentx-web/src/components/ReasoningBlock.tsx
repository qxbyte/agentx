import { BulbOutlined, DownOutlined, LoadingOutlined, RightOutlined } from '@ant-design/icons'
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
        <BulbOutlined />
        <span>{streaming ? '正在思考…' : '思考过程'}</span>
        {streaming && <LoadingOutlined spin />}
        <span style={{ marginLeft: 'auto' }}>{open ? <DownOutlined /> : <RightOutlined />}</span>
      </button>
      {open && <div className="ax-reasoning-content">{content}</div>}
    </div>
  )
}

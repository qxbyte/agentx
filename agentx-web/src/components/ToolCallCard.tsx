import {
  CheckCircleOutlined,
  DownOutlined,
  LoadingOutlined,
  RightOutlined,
  ToolOutlined,
} from '@ant-design/icons'
import { useState } from 'react'
import type { ToolCallInfo } from '../types'

function formatValue(value: unknown): string {
  if (value == null) return ''
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2)
    } catch {
      return value
    }
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

interface ToolCallCardProps {
  call: ToolCallInfo
}

/** 可折叠的「工具调用」卡片，默认折叠 */
export default function ToolCallCard({ call }: ToolCallCardProps) {
  const [open, setOpen] = useState(false)
  const finished = call.done === true || call.result !== undefined
  const args = formatValue(call.args)
  const result = formatValue(call.result)

  return (
    <div className="ax-toolcall">
      <button
        type="button"
        className="ax-toolcall-header"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
      >
        <ToolOutlined />
        <span>工具调用</span>
        <span className="ax-toolcall-name">{call.name}</span>
        <span className="ax-toolcall-status">
          {finished ? (
            <>
              <CheckCircleOutlined style={{ color: 'var(--ax-success)' }} /> 已完成
            </>
          ) : (
            <>
              <LoadingOutlined spin /> 运行中
            </>
          )}
          {open ? <DownOutlined /> : <RightOutlined />}
        </span>
      </button>
      {open && (
        <div className="ax-toolcall-body">
          {args && (
            <>
              <div className="ax-toolcall-label">参数</div>
              <pre className="ax-toolcall-pre ax-scroll">{args}</pre>
            </>
          )}
          {finished && result && (
            <>
              <div className="ax-toolcall-label">结果</div>
              <pre className="ax-toolcall-pre ax-scroll">{result}</pre>
            </>
          )}
          {!args && !result && <div className="ax-toolcall-label">无参数</div>}
        </div>
      )}
    </div>
  )
}

import { useState } from 'react'
import type { ContextUsage } from '../api/chat'

/** token 数 → 「12.3k」样式短文本 */
function formatK(n: number): string {
  return n >= 1000 ? `${(n / 1000).toFixed(n >= 10_000 ? 0 : 1)}k` : String(n)
}

/**
 * 上下文余量指示环：环形进度 = 当前记忆量 / 自动压缩阈值。
 * 环满即触发自动压缩；接近时变琥珀/红提醒可先手动 /compact。
 * 悬浮展示说明卡片（非气泡提示）：标题 + 进度条 + 用量明细 + 压缩说明。
 */
export default function ContextRing({ usage }: { usage: ContextUsage }) {
  const [open, setOpen] = useState(false)
  const pct = Math.min(1, usage.tokens / usage.compactThreshold)
  const size = 18
  const stroke = 2.5
  const r = (size - stroke) / 2
  const circumference = 2 * Math.PI * r
  const color =
    pct >= 0.9 ? 'var(--ax-danger)' : pct >= 0.7 ? 'var(--ax-warning)' : 'var(--ax-text-faint)'

  return (
    <span
      className="relative flex cursor-default items-center"
      aria-label="上下文用量"
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
    >
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke="var(--ax-border)"
          strokeWidth={stroke}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke={color}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - pct)}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
          style={{ transition: 'stroke-dashoffset 0.4s ease, stroke 0.4s ease' }}
        />
      </svg>
      {open && (
        <div className="ax-ctx-card" role="status">
          <div className="ax-ctx-card-head">
            <span>上下文用量</span>
            <span className="ax-ctx-card-pct" style={{ color }}>
              {Math.round(pct * 100)}%
            </span>
          </div>
          <div className="ax-ctx-bar">
            <span className="ax-ctx-bar-fill" style={{ width: `${pct * 100}%`, background: color }} />
          </div>
          <div className="ax-ctx-card-row">
            约 {formatK(usage.tokens)} / {formatK(usage.compactThreshold)} tokens
          </div>
          <div className="ax-ctx-card-hint">
            到达阈值自动压缩早期对话；输入 /compact 可随时手动压缩
          </div>
        </div>
      )}
    </span>
  )
}

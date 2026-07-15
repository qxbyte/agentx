import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import * as adminApi from '../../api/admin'
import { extractErrorMessage } from '../../api/http'
import ErrorState from '../../components/ErrorState'
import PageHeader from '../../components/PageHeader'
import type { DailyTokenStat, ModelTokenStat, TokenStatsSummary } from '../../types'

function formatNumber(v: number | string | null | undefined): string {
  if (v === null || v === undefined || v === '') return '—'
  const n = typeof v === 'string' ? Number(v) : v
  return Number.isFinite(n) ? n.toLocaleString('zh-CN') : String(v)
}

/** 日期串（可能是 ISO 时间戳）→ 本地「MM-DD」；无法解析时退回原串前 10 位。 */
function formatDay(v: string | null | undefined): string {
  if (!v) return ''
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return v.slice(0, 10)
  return `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** by-model 常见字段的中文列名，未命中的字段原样展示 */
const MODEL_COLUMN_LABELS: Record<string, string> = {
  model_type: '类型',
  model: '模型',
  calls: '调用次数',
  total_tokens: '总 Tokens',
  model_name: '模型',
  modelName: '模型',
  provider_type: '提供商',
  providerType: '提供商',
  total_calls: '调用次数',
  prompt_tokens: 'Prompt Tokens',
  completion_tokens: 'Completion Tokens',
  error_calls: '失败次数',
}

export default function StatsPage() {
  const [summary, setSummary] = useState<TokenStatsSummary | null>(null)
  const [daily, setDaily] = useState<DailyTokenStat[]>([])
  const [byModel, setByModel] = useState<ModelTokenStat[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [hoveredDay, setHoveredDay] = useState<number | null>(null)

  const load = useCallback(() => {
    setLoading(true)
    void Promise.allSettled([
      adminApi.fetchTokenSummary(),
      adminApi.fetchDailyTokenStats(14),
      adminApi.fetchModelTokenStats(),
    ]).then(([summaryResult, dailyResult, byModelResult]) => {
      if (summaryResult.status === 'fulfilled') setSummary(summaryResult.value)
      if (dailyResult.status === 'fulfilled') setDaily(dailyResult.value)
      if (byModelResult.status === 'fulfilled') setByModel(byModelResult.value)
      const results = [summaryResult, dailyResult, byModelResult]
      const failed = results.filter((r) => r.status === 'rejected')
      if (failed.length === results.length) {
        // 全部失败：整页错误态 + 重试
        setLoadError(
          extractErrorMessage((failed[0] as PromiseRejectedResult).reason, '加载统计数据失败'),
        )
      } else {
        setLoadError(null)
        if (failed.length > 0) {
          toast.error(
            extractErrorMessage((failed[0] as PromiseRejectedResult).reason, '部分统计数据加载失败'),
          )
        }
      }
      setLoading(false)
    })
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const header = (
    <PageHeader title="用量统计" description="平台模型调用与 Token 消耗概览（近 14 天）" />
  )

  if (loading) {
    return (
      <div aria-busy="true">
        {header}
        <div className="ax-stat-grid">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="ax-stat-card">
              <div className="flex animate-pulse flex-col gap-3">
                <div className="h-3 w-2/5 rounded bg-muted" />
                <div className="h-5 w-3/5 rounded bg-muted" />
              </div>
            </div>
          ))}
        </div>
        <div className="ax-chart-card">
          <div className="flex animate-pulse flex-col gap-3">
            <div className="h-4 w-40 rounded bg-muted" />
            <div className="h-32 w-full rounded bg-muted" />
          </div>
        </div>
      </div>
    )
  }

  if (loadError) {
    return (
      <div>
        {header}
        <ErrorState message={loadError} onRetry={load} />
      </div>
    )
  }

  const dailyTotals = daily.map((d) => (d.prompt_tokens ?? 0) + (d.completion_tokens ?? 0))
  const maxDaily = Math.max(1, ...dailyTotals)

  // by-model 动态列：以首行 key 为准
  const firstRow = byModel[0]
  const modelKeys = firstRow ? Object.keys(firstRow) : []
  const cellText = (key: string, v: unknown): string => {
    if (typeof v === 'number' || /tokens|calls/i.test(key)) return formatNumber(v as number | string)
    return (v ?? '—') as string
  }

  return (
    <div>
      {header}
      <div className="ax-stat-grid">
        <div className="ax-stat-card">
          <div className="ax-stat-label">总调用次数</div>
          <div className="ax-stat-value">{formatNumber(summary?.total_calls)}</div>
        </div>
        <div className="ax-stat-card">
          <div className="ax-stat-label">Prompt Tokens</div>
          <div className="ax-stat-value">{formatNumber(summary?.prompt_tokens)}</div>
        </div>
        <div className="ax-stat-card">
          <div className="ax-stat-label">Completion Tokens</div>
          <div className="ax-stat-value">{formatNumber(summary?.completion_tokens)}</div>
        </div>
        <div className="ax-stat-card">
          <div className="ax-stat-label">失败调用</div>
          <div
            className={`ax-stat-value${(summary?.error_calls ?? 0) > 0 ? ' ax-stat-value--error' : ''}`}
          >
            {formatNumber(summary?.error_calls)}
          </div>
        </div>
      </div>

      <div className="ax-chart-card">
        <h3 className="ax-chart-title">近 14 天 Token 用量</h3>
        {daily.length === 0 ? (
          <div className="py-8 text-center text-sm text-muted-foreground">暂无用量数据</div>
        ) : (
          (() => {
            const n = daily.length
            const active = hoveredDay != null ? daily[hoveredDay] : null
            // 柱高缩放到柱区 72%，留顶部净空给跟随卡片；卡片底边跟随柱顶之上
            const barPct = (v: number) => Math.max(2, Math.round((v / maxDaily) * 72))
            const activePct = hoveredDay != null ? barPct(dailyTotals[hoveredDay] ?? 0) : 0
            return (
              <div className="ax-bars-wrap" onMouseLeave={() => setHoveredDay(null)}>
                <div className="ax-bars">
                  {daily.map((d, i) => {
                    const total = dailyTotals[i] ?? 0
                    return (
                      <div
                        key={d.date || String(i)}
                        className={`ax-bar-col${hoveredDay === i ? ' is-active' : ''}`}
                      >
                        {/* hover 目标是柱子本身，而非整条通高的列，避免鼠标落在柱子上方/旁边空白也命中 */}
                        <div
                          className="ax-bar"
                          style={{ height: `${barPct(total)}%` }}
                          onMouseEnter={() => setHoveredDay(i)}
                          onMouseLeave={() => setHoveredDay(null)}
                        />
                      </div>
                    )
                  })}
                  {/* 单张卡片跟随悬停柱子滑动（弹性 back-ease），不为每根柱子各建一个 tooltip */}
                  <div
                    className={`ax-bar-tip${active ? ' is-shown' : ''}`}
                    style={{
                      left: `${((Math.max(0, hoveredDay ?? 0) + 0.5) / n) * 100}%`,
                      bottom: `calc(${activePct}% + 16px)`,
                    }}
                  >
                    {active && (
                      <>
                        <div className="ax-bar-tip-date">{formatDay(active.date)}</div>
                        <div className="ax-bar-tip-row">
                          <span>调用</span>
                          <b>{formatNumber(active.total_calls ?? 0)}</b>
                        </div>
                        <div className="ax-bar-tip-row">
                          <span>Prompt</span>
                          <b>{formatNumber(active.prompt_tokens ?? 0)}</b>
                        </div>
                        <div className="ax-bar-tip-row">
                          <span>Completion</span>
                          <b>{formatNumber(active.completion_tokens ?? 0)}</b>
                        </div>
                      </>
                    )}
                  </div>
                </div>
                <div className="ax-bars-axis">
                  {daily.map((d, i) => (
                    <span
                      key={d.date || String(i)}
                      className={`ax-axis-label${hoveredDay === i ? ' is-active' : ''}`}
                    >
                      {formatDay(d.date)}
                    </span>
                  ))}
                </div>
              </div>
            )
          })()
        )}
      </div>

      <div className="ax-chart-card overflow-hidden p-0">
        <h3 className="ax-chart-title px-5 pt-[18px]">按模型统计</h3>
        {byModel.length === 0 ? (
          <div className="px-5 py-8 text-center text-sm text-muted-foreground">
            暂无按模型统计数据，产生调用后自动出现
          </div>
        ) : (
          <div className="mt-1.5 px-2 pb-2">
            <Table>
              <TableHeader>
                <TableRow>
                  {modelKeys.map((key) => (
                    <TableHead key={key}>{MODEL_COLUMN_LABELS[key] ?? key}</TableHead>
                  ))}
                </TableRow>
              </TableHeader>
              <TableBody>
                {byModel.map((row, index) => (
                  <TableRow key={index}>
                    {modelKeys.map((key) => (
                      <TableCell key={key}>{cellText(key, (row as Record<string, unknown>)[key])}</TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </div>
    </div>
  )
}

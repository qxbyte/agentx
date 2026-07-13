import { App as AntdApp, Empty, Skeleton, Table, Tooltip } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useEffect, useState } from 'react'
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

/** by-model 常见字段的中文列名，未命中的字段原样展示 */
const MODEL_COLUMN_LABELS: Record<string, string> = {
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
  const { message } = AntdApp.useApp()

  const [summary, setSummary] = useState<TokenStatsSummary | null>(null)
  const [daily, setDaily] = useState<DailyTokenStat[]>([])
  const [byModel, setByModel] = useState<ModelTokenStat[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

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
          message.error(
            extractErrorMessage((failed[0] as PromiseRejectedResult).reason, '部分统计数据加载失败'),
          )
        }
      }
      setLoading(false)
    })
  }, [message])

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
              <Skeleton active title={{ width: '45%' }} paragraph={{ rows: 1, width: '65%' }} />
            </div>
          ))}
        </div>
        <div className="ax-chart-card">
          <Skeleton active title={{ width: 160 }} paragraph={{ rows: 4 }} />
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
  const modelColumns: ColumnsType<ModelTokenStat> = firstRow
    ? Object.keys(firstRow).map((key) => ({
        title: MODEL_COLUMN_LABELS[key] ?? key,
        dataIndex: key,
        render: (v: string | number | null) =>
          typeof v === 'number' || /tokens|calls/i.test(key) ? formatNumber(v) : (v ?? '—'),
      }))
    : []

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
          <div className={`ax-stat-value${(summary?.error_calls ?? 0) > 0 ? ' ax-stat-value--error' : ''}`}>
            {formatNumber(summary?.error_calls)}
          </div>
        </div>
      </div>

      <div className="ax-chart-card">
        <h3 className="ax-chart-title">近 14 天 Token 用量</h3>
        {daily.length === 0 ? (
          <Empty description="暂无用量数据" />
        ) : (
          <div className="ax-bars">
            {daily.map((d, i) => {
              const total = dailyTotals[i] ?? 0
              return (
                <Tooltip
                  key={d.date || String(i)}
                  title={
                    <>
                      <div>{d.date}</div>
                      <div>调用 {formatNumber(d.total_calls ?? 0)} 次</div>
                      <div>Prompt {formatNumber(d.prompt_tokens ?? 0)}</div>
                      <div>Completion {formatNumber(d.completion_tokens ?? 0)}</div>
                    </>
                  }
                >
                  <div className="ax-bar-col">
                    <div
                      className="ax-bar"
                      style={{ height: `${Math.max(1, Math.round((total / maxDaily) * 100))}%` }}
                    />
                    <span className="ax-bar-label">{d.date ? d.date.slice(5) : ''}</span>
                  </div>
                </Tooltip>
              )
            })}
          </div>
        )}
      </div>

      <div className="ax-chart-card" style={{ padding: 0, overflow: 'hidden' }}>
        <h3 className="ax-chart-title" style={{ padding: '18px 20px 0' }}>
          按模型统计
        </h3>
        <Table<ModelTokenStat>
          rowKey={(_, index) => String(index)}
          columns={modelColumns}
          dataSource={byModel}
          pagination={false}
          size="middle"
          scroll={{ x: 640 }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                style={{ padding: '24px 0' }}
                description="暂无按模型统计数据，产生调用后自动出现"
              />
            ),
          }}
          style={{ marginTop: 6 }}
        />
      </div>
    </div>
  )
}

import { FileTextOutlined } from '@ant-design/icons'
import { App as AntdApp, Button, Drawer, Empty, Switch, Table, Tag, Tooltip } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useEffect, useState } from 'react'
import * as adminApi from '../../api/admin'
import * as agentsApi from '../../api/agents'
import { extractErrorMessage } from '../../api/http'
import ErrorState from '../../components/ErrorState'
import PageHeader from '../../components/PageHeader'
import type { ToolSource, ToolView } from '../../types'

/** paramsSchema 可能是 JSON 字符串或对象，统一美化输出 */
function formatSchema(schema: ToolView['paramsSchema']): string {
  if (schema === null || schema === undefined || schema === '') return '（无参数定义）'
  if (typeof schema === 'string') {
    try {
      return JSON.stringify(JSON.parse(schema), null, 2)
    } catch {
      return schema
    }
  }
  return JSON.stringify(schema, null, 2)
}

export default function ToolsPage() {
  const { message } = AntdApp.useApp()

  const [tools, setTools] = useState<ToolView[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [schemaTool, setSchemaTool] = useState<ToolView | null>(null)

  const refresh = useCallback(() => {
    agentsApi
      .listTools()
      .then((list) => {
        setTools(list)
        setLoadError(null)
      })
      .catch((error: unknown) => setLoadError(extractErrorMessage(error, '加载工具目录失败')))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const handleToggle = async (tool: ToolView, enabled: boolean) => {
    setTools((prev) => prev.map((t) => (t.name === tool.name ? { ...t, enabled } : t)))
    try {
      await adminApi.toggleToolEnabled(tool.name, enabled)
    } catch (error) {
      setTools((prev) => prev.map((t) => (t.name === tool.name ? { ...t, enabled: !enabled } : t)))
      message.error(extractErrorMessage(error, '操作失败'))
    }
  }

  const columns: ColumnsType<ToolView> = [
    {
      title: '工具名',
      dataIndex: 'name',
      width: 220,
      render: (v: string) => (
        <span style={{ fontFamily: 'var(--ax-mono)', fontSize: 12.5, fontWeight: 600 }}>{v}</span>
      ),
    },
    {
      title: '来源',
      dataIndex: 'source',
      width: 90,
      render: (v: ToolSource) => <Tag color={v === 'CODE' ? 'geekblue' : 'purple'}>{v}</Tag>,
    },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    {
      title: '参数',
      key: 'schema',
      width: 80,
      render: (_, tool) => (
        <Tooltip title="查看参数 Schema">
          <Button
            type="text"
            size="small"
            icon={<FileTextOutlined />}
            onClick={() => setSchemaTool(tool)}
          />
        </Tooltip>
      ),
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 80,
      render: (enabled: boolean, tool) => (
        <Switch
          size="small"
          checked={enabled}
          onChange={(checked) => void handleToggle(tool, checked)}
        />
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="工具目录"
        description="平台内置与 MCP 服务提供的全部工具，停用后 Agent 将无法调用"
      />

      {loadError ? (
        <ErrorState
          message={loadError}
          onRetry={() => {
            setLoading(true)
            refresh()
          }}
        />
      ) : (
        <Table<ToolView>
          rowKey="name"
          columns={columns}
          dataSource={tools}
          loading={loading}
          pagination={false}
          size="middle"
          scroll={{ x: 700 }}
          locale={{
            emptyText: loading ? (
              ' '
            ) : (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                style={{ padding: '24px 0' }}
                description="暂无可用工具，接入 MCP 服务后这里会自动出现其提供的工具"
              />
            ),
          }}
        />
      )}

      <Drawer
        title={schemaTool ? `参数 Schema · ${schemaTool.name}` : '参数 Schema'}
        width={520}
        open={schemaTool !== null}
        onClose={() => setSchemaTool(null)}
        destroyOnHidden
      >
        {schemaTool && (
          <>
            <p style={{ marginTop: 0, color: 'var(--ax-text-secondary)' }}>
              {schemaTool.description || '无描述'}
            </p>
            <pre className="ax-toolcall-pre" style={{ maxHeight: 'none' }}>
              {formatSchema(schemaTool.paramsSchema)}
            </pre>
          </>
        )}
      </Drawer>
    </div>
  )
}

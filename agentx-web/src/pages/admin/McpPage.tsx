import {
  ApiOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { App as AntdApp, Button, Empty, Form, Input, List, Modal, Select, Switch, Table, Tag, Tooltip } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useState } from 'react'
import * as adminApi from '../../api/admin'
import { extractErrorMessage } from '../../api/http'
import ErrorState from '../../components/ErrorState'
import PageHeader from '../../components/PageHeader'
import type { McpServer, McpToolPreview, McpTransport } from '../../types'

/** 弹窗表单值（connectParams 拆成友好字段，提交时再组装 JSON） */
interface McpFormValues {
  name: string
  transport: McpTransport
  enabled: boolean
  command?: string
  /** 每行一个参数 */
  args?: string
  /** 每行一条 KEY=VALUE */
  env?: string
  url?: string
  /** 每行一条 Key: Value */
  headers?: string
}

function parseLines(text: string | undefined): string[] {
  return (text ?? '')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
}

function parseKvLines(text: string | undefined, sep: string): Record<string, string> {
  const result: Record<string, string> = {}
  for (const line of parseLines(text)) {
    const idx = line.indexOf(sep)
    if (idx <= 0) continue
    result[line.slice(0, idx).trim()] = line.slice(idx + sep.length).trim()
  }
  return result
}

function buildConnectParams(values: McpFormValues): string {
  if (values.transport === 'STDIO') {
    return JSON.stringify({
      command: values.command ?? '',
      args: parseLines(values.args),
      env: parseKvLines(values.env, '='),
    })
  }
  return JSON.stringify({
    url: values.url ?? '',
    headers: parseKvLines(values.headers, ':'),
  })
}

/** 回显：connectParams JSON → 表单友好字段 */
function connectParamsToFields(server: McpServer): Partial<McpFormValues> {
  if (!server.connectParams) return {}
  try {
    const parsed = JSON.parse(server.connectParams) as Record<string, unknown>
    if (server.transport === 'STDIO') {
      const args = Array.isArray(parsed.args) ? (parsed.args as unknown[]) : []
      const env = (parsed.env ?? {}) as Record<string, unknown>
      return {
        command: typeof parsed.command === 'string' ? parsed.command : '',
        args: args.map(String).join('\n'),
        env: Object.entries(env)
          .map(([k, v]) => `${k}=${String(v)}`)
          .join('\n'),
      }
    }
    const headers = (parsed.headers ?? {}) as Record<string, unknown>
    return {
      url: typeof parsed.url === 'string' ? parsed.url : '',
      headers: Object.entries(headers)
        .map(([k, v]) => `${k}: ${String(v)}`)
        .join('\n'),
    }
  } catch {
    return {}
  }
}

export default function McpPage() {
  const { message, modal } = AntdApp.useApp()
  const [form] = Form.useForm<McpFormValues>()
  const transport = Form.useWatch('transport', form)

  const [servers, setServers] = useState<McpServer[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<McpServer | null>(null)
  const [saving, setSaving] = useState(false)
  const [testingId, setTestingId] = useState<string | null>(null)
  const [testResult, setTestResult] = useState<{ name: string; tools: McpToolPreview[] } | null>(
    null,
  )

  const refresh = async () => {
    try {
      setServers(await adminApi.listMcpServers())
      setLoadError(null)
    } catch (error) {
      setLoadError(extractErrorMessage(error, '加载 MCP 服务失败'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refresh()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ transport: 'STDIO', enabled: true })
    setModalOpen(true)
  }

  const openEdit = (server: McpServer) => {
    setEditing(server)
    form.resetFields()
    form.setFieldsValue({
      name: server.name,
      transport: server.transport,
      enabled: server.enabled,
      ...connectParamsToFields(server),
    })
    setModalOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    const payload: adminApi.McpServerPayload = {
      name: values.name,
      transport: values.transport,
      connectParams: buildConnectParams(values),
      enabled: values.enabled,
    }
    setSaving(true)
    try {
      if (editing) {
        await adminApi.updateMcpServer(editing.id, payload)
        message.success('MCP 服务已更新')
      } else {
        await adminApi.createMcpServer(payload)
        message.success('MCP 服务已创建')
      }
      setModalOpen(false)
      await refresh()
    } catch (error) {
      message.error(extractErrorMessage(error, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  /** 启停：后端只有 PUT 全量更新，透传原 connectParams */
  const handleToggle = async (server: McpServer, enabled: boolean) => {
    setServers((prev) => prev.map((s) => (s.id === server.id ? { ...s, enabled } : s)))
    try {
      await adminApi.updateMcpServer(server.id, {
        name: server.name,
        transport: server.transport,
        connectParams: server.connectParams ?? '{}',
        enabled,
      })
    } catch (error) {
      setServers((prev) => prev.map((s) => (s.id === server.id ? { ...s, enabled: !enabled } : s)))
      message.error(extractErrorMessage(error, '操作失败'))
    }
  }

  const handleTest = async (server: McpServer) => {
    setTestingId(server.id)
    try {
      const tools = await adminApi.testMcpConnection(server.id)
      setTestResult({ name: server.name, tools })
    } catch (error) {
      message.error(extractErrorMessage(error, '连接测试失败'))
    } finally {
      setTestingId(null)
    }
  }

  const confirmDelete = (server: McpServer) => {
    modal.confirm({
      title: '删除 MCP 服务',
      content: `确定删除「${server.name}」吗？其提供的远程工具将不可用。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      async onOk() {
        try {
          await adminApi.deleteMcpServer(server.id)
          message.success('已删除')
          await refresh()
        } catch (error) {
          message.error(extractErrorMessage(error, '删除失败'))
        }
      },
    })
  }

  const columns: ColumnsType<McpServer> = [
    { title: '名称', dataIndex: 'name', ellipsis: true },
    {
      title: '传输方式',
      dataIndex: 'transport',
      width: 170,
      render: (v: McpTransport) => (
        <Tag color={v === 'STDIO' ? 'geekblue' : 'purple'}>{v}</Tag>
      ),
    },
    {
      title: '连接参数',
      dataIndex: 'connectParams',
      ellipsis: true,
      render: (v: string | null) => (
        <span style={{ fontFamily: 'var(--ax-mono)', fontSize: 12 }}>{v || '—'}</span>
      ),
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 80,
      render: (enabled: boolean, record) => (
        <Switch
          size="small"
          checked={enabled}
          onChange={(checked) => void handleToggle(record, checked)}
        />
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      render: (_, record) => (
        <>
          <Tooltip title="测试连接">
            <Button
              type="text"
              size="small"
              icon={<ThunderboltOutlined />}
              loading={testingId === record.id}
              onClick={() => void handleTest(record)}
            />
          </Tooltip>
          <Button type="text" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)} />
          <Button
            type="text"
            size="small"
            danger
            icon={<DeleteOutlined />}
            onClick={() => confirmDelete(record)}
          />
        </>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="MCP 服务"
        description="接入 Model Context Protocol 服务，为 Agent 扩展远程工具能力"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建 MCP 服务
          </Button>
        }
      />

      {loadError ? (
        <ErrorState
          message={loadError}
          onRetry={() => {
            setLoading(true)
            void refresh()
          }}
        />
      ) : (
        <Table<McpServer>
          rowKey="id"
          columns={columns}
          dataSource={servers}
          loading={loading}
          pagination={false}
          size="middle"
          scroll={{ x: 760 }}
          locale={{
            emptyText: loading ? (
              ' '
            ) : (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                style={{ padding: '24px 0' }}
                description="还没有 MCP 服务，接入一个为 Agent 扩展工具"
              >
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  新建 MCP 服务
                </Button>
              </Empty>
            ),
          }}
        />
      )}

      <Modal
        title={editing ? '编辑 MCP 服务' : '新建 MCP 服务'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => void handleSave()}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="例如：filesystem" maxLength={60} />
          </Form.Item>
          <Form.Item
            name="transport"
            label="传输方式"
            rules={[{ required: true, message: '请选择传输方式' }]}
          >
            <Select
              options={[
                { value: 'STDIO', label: 'STDIO（本地进程）' },
                { value: 'STREAMABLE_HTTP', label: 'Streamable HTTP（远程服务）' },
              ]}
            />
          </Form.Item>

          {transport === 'STREAMABLE_HTTP' ? (
            <>
              <Form.Item
                name="url"
                label="URL"
                rules={[{ required: true, message: '请输入服务 URL' }]}
              >
                <Input placeholder="https://mcp.example.com/mcp" />
              </Form.Item>
              <Form.Item
                name="headers"
                label="请求头（每行一条，格式 Key: Value）"
              >
                <Input.TextArea rows={3} placeholder={'Authorization: Bearer xxx'} />
              </Form.Item>
            </>
          ) : (
            <>
              <Form.Item
                name="command"
                label="启动命令"
                rules={[{ required: true, message: '请输入启动命令' }]}
              >
                <Input placeholder="npx" />
              </Form.Item>
              <Form.Item name="args" label="参数（每行一个）">
                <Input.TextArea rows={3} placeholder={'-y\n@modelcontextprotocol/server-filesystem\n/data'} />
              </Form.Item>
              <Form.Item name="env" label="环境变量（每行一条，格式 KEY=VALUE）">
                <Input.TextArea rows={2} placeholder={'API_TOKEN=xxx'} />
              </Form.Item>
            </>
          )}

          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={testResult ? `连接成功 · ${testResult.name}` : '连接结果'}
        open={testResult !== null}
        onCancel={() => setTestResult(null)}
        footer={
          <Button type="primary" onClick={() => setTestResult(null)}>
            关闭
          </Button>
        }
        destroyOnHidden
      >
        <List
          dataSource={testResult?.tools ?? []}
          locale={{ emptyText: '连接成功，但该服务未暴露任何工具' }}
          renderItem={(tool) => (
            <List.Item>
              <List.Item.Meta
                avatar={<ApiOutlined style={{ fontSize: 16, color: 'var(--ax-primary)' }} />}
                title={<span style={{ fontFamily: 'var(--ax-mono)', fontSize: 13 }}>{tool.name}</span>}
                description={tool.description || '无描述'}
              />
            </List.Item>
          )}
        />
      </Modal>
    </div>
  )
}

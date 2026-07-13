import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import {
  App as AntdApp,
  Button,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Switch,
  Table,
  Tag,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useState } from 'react'
import * as adminApi from '../../api/admin'
import * as agentsApi from '../../api/agents'
import { extractErrorMessage } from '../../api/http'
import * as kbApi from '../../api/kb'
import ErrorState from '../../components/ErrorState'
import PageHeader from '../../components/PageHeader'
import type { AgentPayload, AgentView, KnowledgeBase, ToolView, WorkflowType } from '../../types'

const WORKFLOW_OPTIONS: { value: WorkflowType; label: string }[] = [
  { value: 'REACT', label: 'ReAct（推理 + 工具循环）' },
  { value: 'CHAIN', label: 'Chain（提示链）' },
  { value: 'ROUTING', label: 'Routing（路由分发）' },
  { value: 'PARALLELIZATION', label: 'Parallelization（并行聚合）' },
  { value: 'ORCHESTRATOR_WORKERS', label: 'Orchestrator-Workers（编排）' },
  { value: 'EVALUATOR_OPTIMIZER', label: 'Evaluator-Optimizer（评审优化）' },
]

/** 后端 View 的 toolNames/kbIds 是 JSON 字符串，安全解析为 string[] */
function parseJsonArray(raw: string | null): string[] {
  if (!raw) return []
  try {
    const parsed: unknown = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter((v): v is string => typeof v === 'string') : []
  } catch {
    return []
  }
}

export default function AgentsPage() {
  const { message, modal } = AntdApp.useApp()
  const [form] = Form.useForm<AgentPayload>()

  const [agents, setAgents] = useState<AgentView[]>([])
  const [tools, setTools] = useState<ToolView[]>([])
  const [kbs, setKbs] = useState<KnowledgeBase[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<AgentView | null>(null)
  const [saving, setSaving] = useState(false)

  const refresh = async () => {
    try {
      setAgents(await adminApi.listAdminAgents())
      setLoadError(null)
    } catch (error) {
      setLoadError(extractErrorMessage(error, '加载 Agent 列表失败'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refresh()
    // 表单多选数据源：工具目录 + 知识库（失败不阻塞列表）
    void agentsApi.listTools().then(setTools).catch(() => setTools([]))
    void kbApi.listKbs().then(setKbs).catch(() => setKbs([]))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const kbNameById = new Map(kbs.map((kb) => [kb.id, kb.name]))

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({
      workflowType: 'REACT',
      toolNames: [],
      kbIds: [],
      maxIterations: 10,
      enabled: true,
    })
    setModalOpen(true)
  }

  const openEdit = (agent: AgentView) => {
    setEditing(agent)
    form.resetFields()
    form.setFieldsValue({
      name: agent.name,
      description: agent.description ?? undefined,
      systemPrompt: agent.systemPrompt ?? undefined,
      workflowType: agent.workflowType,
      toolNames: parseJsonArray(agent.toolNames),
      kbIds: parseJsonArray(agent.kbIds),
      maxIterations: agent.maxIterations,
      enabled: agent.enabled,
    })
    setModalOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      if (editing) {
        await adminApi.updateAgent(editing.id, values)
        message.success('Agent 已更新')
      } else {
        await adminApi.createAgent(values)
        message.success('Agent 已创建')
      }
      setModalOpen(false)
      await refresh()
    } catch (error) {
      message.error(extractErrorMessage(error, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  /** 启停：PUT 全量更新（toolNames/kbIds 需先 parse 回数组） */
  const handleToggle = async (agent: AgentView, enabled: boolean) => {
    setAgents((prev) => prev.map((a) => (a.id === agent.id ? { ...a, enabled } : a)))
    try {
      await adminApi.updateAgent(agent.id, {
        name: agent.name,
        ...(agent.description ? { description: agent.description } : {}),
        ...(agent.systemPrompt ? { systemPrompt: agent.systemPrompt } : {}),
        workflowType: agent.workflowType,
        toolNames: parseJsonArray(agent.toolNames),
        kbIds: parseJsonArray(agent.kbIds),
        maxIterations: agent.maxIterations,
        enabled,
      })
    } catch (error) {
      setAgents((prev) => prev.map((a) => (a.id === agent.id ? { ...a, enabled: !enabled } : a)))
      message.error(extractErrorMessage(error, '操作失败'))
    }
  }

  const confirmDelete = (agent: AgentView) => {
    modal.confirm({
      title: '删除 Agent',
      content: `确定删除「${agent.name}」吗？`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      async onOk() {
        try {
          await adminApi.deleteAgent(agent.id)
          message.success('已删除')
          await refresh()
        } catch (error) {
          message.error(extractErrorMessage(error, '删除失败'))
        }
      },
    })
  }

  const columns: ColumnsType<AgentView> = [
    { title: '名称', dataIndex: 'name', ellipsis: true },
    {
      title: '工作流',
      dataIndex: 'workflowType',
      width: 150,
      render: (v: WorkflowType) => <Tag color="geekblue">{v}</Tag>,
    },
    {
      title: '工具',
      dataIndex: 'toolNames',
      ellipsis: true,
      render: (raw: string | null) => {
        const names = parseJsonArray(raw)
        return names.length === 0
          ? '—'
          : names.map((n) => (
              <Tag key={n} style={{ fontFamily: 'var(--ax-mono)', fontSize: 11.5 }}>
                {n}
              </Tag>
            ))
      },
    },
    {
      title: '知识库',
      dataIndex: 'kbIds',
      ellipsis: true,
      render: (raw: string | null) => {
        const ids = parseJsonArray(raw)
        return ids.length === 0
          ? '—'
          : ids.map((id) => <Tag key={id}>{kbNameById.get(id) ?? id}</Tag>)
      },
    },
    { title: '最大迭代', dataIndex: 'maxIterations', width: 90, align: 'right' },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 80,
      render: (enabled: boolean, agent) => (
        <Switch
          size="small"
          checked={enabled}
          onChange={(checked) => void handleToggle(agent, checked)}
        />
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_, agent) => (
        <>
          <Button type="text" size="small" icon={<EditOutlined />} onClick={() => openEdit(agent)} />
          <Button
            type="text"
            size="small"
            danger
            icon={<DeleteOutlined />}
            onClick={() => confirmDelete(agent)}
          />
        </>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="Agent"
        description="编排工作流、工具与知识库，构建面向业务场景的智能体"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建 Agent
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
        <Table<AgentView>
          rowKey="id"
          columns={columns}
          dataSource={agents}
          loading={loading}
          pagination={false}
          size="middle"
          scroll={{ x: 900 }}
          locale={{
            emptyText: loading ? (
              ' '
            ) : (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                style={{ padding: '24px 0' }}
                description="还没有 Agent，创建第一个智能体开始编排"
              >
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  新建 Agent
                </Button>
              </Empty>
            ),
          }}
        />
      )}

      <Modal
        title={editing ? '编辑 Agent' : '新建 Agent'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => void handleSave()}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        width={640}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="例如：数据分析助手" maxLength={60} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input placeholder="这个 Agent 擅长什么？（可选）" maxLength={200} />
          </Form.Item>
          <Form.Item name="systemPrompt" label="系统提示词">
            <Input.TextArea
              rows={5}
              placeholder="定义 Agent 的角色、能力边界与回复风格…"
            />
          </Form.Item>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', columnGap: 16 }}>
            <Form.Item
              name="workflowType"
              label="工作流类型"
              rules={[{ required: true, message: '请选择工作流' }]}
            >
              <Select options={WORKFLOW_OPTIONS} />
            </Form.Item>
            <Form.Item
              name="maxIterations"
              label="最大迭代次数"
              extra="工作流内推理与工具调用的最大循环轮数"
              rules={[{ required: true, message: '必填' }]}
            >
              <InputNumber min={1} max={50} style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <Form.Item name="toolNames" label="可用工具">
            <Select
              mode="multiple"
              placeholder="选择该 Agent 可调用的工具"
              options={tools.map((t) => ({
                value: t.name,
                label: t.description ? `${t.name} — ${t.description}` : t.name,
              }))}
              optionFilterProp="label"
            />
          </Form.Item>
          <Form.Item name="kbIds" label="关联知识库">
            <Select
              mode="multiple"
              placeholder="选择该 Agent 可检索的知识库"
              options={kbs.map((kb) => ({ value: kb.id, label: kb.name }))}
              optionFilterProp="label"
            />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

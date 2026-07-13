import { DeleteOutlined, EditOutlined, PlusOutlined, StarFilled, StarOutlined } from '@ant-design/icons'
import { App as AntdApp, Button, Empty, Form, Input, Modal, Select, Switch, Table, Tag, Tooltip } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useState } from 'react'
import * as adminApi from '../../api/admin'
import { extractErrorMessage } from '../../api/http'
import ErrorState from '../../components/ErrorState'
import PageHeader from '../../components/PageHeader'
import type { ModelConfig, ModelConfigPayload, ModelType, ProviderType } from '../../types'

const PROVIDER_OPTIONS: { value: ProviderType; label: string }[] = [
  { value: 'DEEPSEEK', label: 'DeepSeek' },
  { value: 'OPENAI_COMPATIBLE', label: 'OpenAI 兼容' },
  { value: 'OLLAMA', label: 'Ollama' },
]

const TYPE_OPTIONS: { value: ModelType; label: string }[] = [
  { value: 'CHAT', label: '对话（CHAT）' },
  { value: 'EMBEDDING', label: '向量（EMBEDDING）' },
]

/** 从已有记录构造 PUT 全量 payload（不带 apiKey 表示不修改密钥） */
function toPayload(record: ModelConfig, overrides?: Partial<ModelConfigPayload>): ModelConfigPayload {
  return {
    name: record.name,
    providerType: record.providerType,
    baseUrl: record.baseUrl,
    modelName: record.modelName,
    type: record.type,
    enabled: record.enabled,
    ...overrides,
  }
}

export default function ModelsPage() {
  const { message, modal } = AntdApp.useApp()
  const [form] = Form.useForm<ModelConfigPayload>()

  const [configs, setConfigs] = useState<ModelConfig[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ModelConfig | null>(null)
  const [saving, setSaving] = useState(false)

  const refresh = async () => {
    try {
      setConfigs(await adminApi.listModelConfigs())
      setLoadError(null)
    } catch (error) {
      setLoadError(extractErrorMessage(error, '加载模型配置失败'))
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
    form.setFieldsValue({ providerType: 'OPENAI_COMPATIBLE', type: 'CHAT', enabled: true })
    setModalOpen(true)
  }

  const openEdit = (record: ModelConfig) => {
    setEditing(record)
    form.resetFields()
    form.setFieldsValue(toPayload(record))
    setModalOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    // 编辑时 apiKey 留空 = 不修改，不上送该字段
    if (!values.apiKey) delete values.apiKey
    setSaving(true)
    try {
      if (editing) {
        await adminApi.updateModelConfig(editing.id, values)
        message.success('模型配置已更新')
      } else {
        await adminApi.createModelConfig(values)
        message.success('模型配置已创建')
      }
      setModalOpen(false)
      await refresh()
    } catch (error) {
      message.error(extractErrorMessage(error, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  const handleSetDefault = async (record: ModelConfig) => {
    try {
      await adminApi.setDefaultModelConfig(record.id)
      message.success(`已将「${record.name}」设为默认模型`)
      await refresh()
    } catch (error) {
      message.error(extractErrorMessage(error, '设置默认失败'))
    }
  }

  const handleToggle = async (record: ModelConfig, enabled: boolean) => {
    setConfigs((prev) => prev.map((c) => (c.id === record.id ? { ...c, enabled } : c)))
    try {
      await adminApi.updateModelConfig(record.id, toPayload(record, { enabled }))
    } catch (error) {
      setConfigs((prev) => prev.map((c) => (c.id === record.id ? { ...c, enabled: !enabled } : c)))
      message.error(extractErrorMessage(error, '操作失败'))
    }
  }

  const confirmDelete = (record: ModelConfig) => {
    modal.confirm({
      title: '删除模型配置',
      content: `确定删除「${record.name}」吗？`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      async onOk() {
        try {
          await adminApi.deleteModelConfig(record.id)
          message.success('已删除')
          await refresh()
        } catch (error) {
          message.error(extractErrorMessage(error, '删除失败'))
        }
      },
    })
  }

  const columns: ColumnsType<ModelConfig> = [
    {
      title: '',
      key: 'default',
      width: 44,
      render: (_, record) =>
        record.isDefault ? (
          <Tooltip title="默认模型">
            <StarFilled style={{ color: 'var(--ax-star)', fontSize: 15 }} />
          </Tooltip>
        ) : (
          <Tooltip title="设为默认">
            <Button
              type="text"
              size="small"
              icon={<StarOutlined />}
              onClick={() => void handleSetDefault(record)}
            />
          </Tooltip>
        ),
    },
    { title: '名称', dataIndex: 'name', ellipsis: true },
    {
      title: '提供商',
      dataIndex: 'providerType',
      width: 130,
      render: (v: ProviderType) => (
        <Tag>{PROVIDER_OPTIONS.find((o) => o.value === v)?.label ?? v}</Tag>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 110,
      render: (v: ModelType) => (
        <Tag color={v === 'CHAT' ? 'geekblue' : 'purple'}>{v}</Tag>
      ),
    },
    { title: '模型名', dataIndex: 'modelName', ellipsis: true },
    {
      title: 'API Key',
      dataIndex: 'maskedApiKey',
      width: 140,
      render: (v: string | null | undefined) => (
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
      width: 100,
      render: (_, record) => (
        <>
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
        title="模型配置"
        description="接入对话与向量模型，标星的配置将作为平台默认模型"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建模型配置
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
        <Table<ModelConfig>
          rowKey="id"
          columns={columns}
          dataSource={configs}
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
                description="还没有模型配置，接入第一个模型后即可开始对话"
              >
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  新建模型配置
                </Button>
              </Empty>
            ),
          }}
        />
      )}

      <Modal
        title={editing ? '编辑模型配置' : '新建模型配置'}
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
            <Input placeholder="例如：DeepSeek V3" maxLength={60} />
          </Form.Item>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', columnGap: 16 }}>
            <Form.Item
              name="providerType"
              label="提供商"
              rules={[{ required: true, message: '请选择提供商' }]}
            >
              <Select options={PROVIDER_OPTIONS} />
            </Form.Item>
            <Form.Item name="type" label="模型类型" rules={[{ required: true, message: '请选择类型' }]}>
              <Select options={TYPE_OPTIONS} />
            </Form.Item>
          </div>
          <Form.Item
            name="baseUrl"
            label="Base URL"
            extra="服务接口地址，不含具体路径"
            rules={[{ required: true, message: '请输入 Base URL' }]}
          >
            <Input placeholder="https://api.deepseek.com" />
          </Form.Item>
          <Form.Item
            name="modelName"
            label="模型名"
            rules={[{ required: true, message: '请输入模型名' }]}
          >
            <Input placeholder="deepseek-chat" />
          </Form.Item>
          <Form.Item
            name="apiKey"
            label="API Key"
            rules={editing ? [] : [{ required: true, message: '请输入 API Key' }]}
            extra={editing ? `当前：${editing.maskedApiKey || '未设置'}，留空表示不修改` : undefined}
          >
            <Input.Password placeholder={editing ? '留空保持不变' : 'sk-...'} autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

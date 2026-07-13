import { PlusOutlined } from '@ant-design/icons'
import { App as AntdApp, Button, Empty, Form, Input, Modal, Select, Switch, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useState } from 'react'
import * as adminApi from '../../api/admin'
import { extractErrorMessage } from '../../api/http'
import ErrorState from '../../components/ErrorState'
import PageHeader from '../../components/PageHeader'
import { useAuthStore } from '../../stores/auth'
import type { AdminUser, AdminUserPayload } from '../../types'

export default function UsersPage() {
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<AdminUserPayload>()
  const currentUser = useAuthStore((s) => s.user)

  const [users, setUsers] = useState<AdminUser[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)

  const refresh = async () => {
    try {
      setUsers(await adminApi.listUsers())
      setLoadError(null)
    } catch (error) {
      setLoadError(extractErrorMessage(error, '加载用户列表失败'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refresh()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    form.resetFields()
    form.setFieldsValue({ role: 'USER' })
    setModalOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      await adminApi.createUser(values)
      message.success('用户已创建')
      setModalOpen(false)
      await refresh()
    } catch (error) {
      message.error(extractErrorMessage(error, '创建失败'))
    } finally {
      setSaving(false)
    }
  }

  const handleToggleStatus = async (user: AdminUser, active: boolean) => {
    const next = active ? 'ACTIVE' : 'DISABLED'
    setUsers((prev) => prev.map((u) => (u.id === user.id ? { ...u, status: next } : u)))
    try {
      await adminApi.updateUserStatus(user.id, next)
    } catch (error) {
      setUsers((prev) => prev.map((u) => (u.id === user.id ? { ...u, status: user.status } : u)))
      message.error(extractErrorMessage(error, '操作失败'))
    }
  }

  const columns: ColumnsType<AdminUser> = [
    { title: '用户名', dataIndex: 'username', ellipsis: true },
    { title: '昵称', dataIndex: 'nickname', ellipsis: true },
    {
      title: '角色',
      dataIndex: 'role',
      width: 110,
      render: (v: string) => (
        <Tag color={v === 'ADMIN' ? 'purple' : 'default'}>{v === 'ADMIN' ? '管理员' : '成员'}</Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 130,
      render: (v: string | undefined) => (v ? v.slice(0, 10) : '—'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (status: AdminUser['status'], user) => (
        <Switch
          size="small"
          checked={status === 'ACTIVE'}
          checkedChildren="启用"
          unCheckedChildren="停用"
          disabled={user.id === currentUser?.id}
          onChange={(checked) => void handleToggleStatus(user, checked)}
        />
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="用户"
        description="管理平台成员账号、角色与启用状态"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建用户
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
        <Table<AdminUser>
          rowKey="id"
          columns={columns}
          dataSource={users}
          loading={loading}
          pagination={false}
          size="middle"
          scroll={{ x: 560 }}
          locale={{
            emptyText: loading ? (
              ' '
            ) : (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                style={{ padding: '24px 0' }}
                description="还没有其他成员，创建账号邀请团队使用"
              >
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  新建用户
                </Button>
              </Empty>
            ),
          }}
        />
      )}

      <Modal
        title="新建用户"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => void handleSave()}
        okText="创建"
        cancelText="取消"
        confirmLoading={saving}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input placeholder="登录账号" maxLength={40} autoComplete="off" />
          </Form.Item>
          <Form.Item
            name="password"
            label="初始密码"
            rules={[
              { required: true, message: '请输入初始密码' },
              { min: 6, message: '至少 6 位' },
            ]}
          >
            <Input.Password placeholder="至少 6 位" autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="nickname"
            label="昵称"
            rules={[{ required: true, message: '请输入昵称' }]}
          >
            <Input placeholder="显示名称" maxLength={40} />
          </Form.Item>
          <Form.Item name="role" label="角色" rules={[{ required: true, message: '请选择角色' }]}>
            <Select
              options={[
                { value: 'USER', label: '成员' },
                { value: 'ADMIN', label: '管理员' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

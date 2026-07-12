import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { App as AntdApp, Button, Form, Input } from 'antd'
import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { extractErrorMessage } from '../api/http'
import { getAccessToken } from '../api/tokens'
import Logo from '../components/Logo'
import { useAuthStore } from '../stores/auth'

interface LoginFormValues {
  username: string
  password: string
}

export default function LoginPage() {
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const login = useAuthStore((s) => s.login)
  const [loading, setLoading] = useState(false)

  if (getAccessToken()) {
    return <Navigate to="/" replace />
  }

  const handleFinish = async (values: LoginFormValues) => {
    setLoading(true)
    try {
      await login(values.username, values.password)
      navigate('/', { replace: true })
    } catch (error) {
      message.error(extractErrorMessage(error, '登录失败，请检查用户名和密码'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="ax-login">
      <div className="ax-login-card">
        <div className="ax-login-brand">
          <Logo size={52} />
          <h1 className="ax-login-title">
            欢迎使用 <strong>AgentX</strong>
          </h1>
          <p className="ax-login-sub">企业级智能体平台 · 登录以继续</p>
        </div>
        <Form<LoginFormValues>
          layout="vertical"
          size="large"
          requiredMark={false}
          onFinish={(values) => void handleFinish(values)}
        >
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input
              prefix={<UserOutlined style={{ color: 'var(--ax-text-faint)' }} />}
              placeholder="用户名"
              autoComplete="username"
              autoFocus
            />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined style={{ color: 'var(--ax-text-faint)' }} />}
              placeholder="密码"
              autoComplete="current-password"
            />
          </Form.Item>
          <Form.Item style={{ marginBottom: 8, marginTop: 8 }}>
            <Button type="primary" htmlType="submit" block loading={loading}>
              登 录
            </Button>
          </Form.Item>
        </Form>
      </div>
    </div>
  )
}

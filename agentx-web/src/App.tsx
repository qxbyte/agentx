import { App as AntdApp, ConfigProvider, Spin } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { useEffect } from 'react'
import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { getAccessToken } from './api/tokens'
import AdminLayout from './pages/admin/AdminLayout'
import AgentsPage from './pages/admin/AgentsPage'
import McpPage from './pages/admin/McpPage'
import ModelsPage from './pages/admin/ModelsPage'
import StatsPage from './pages/admin/StatsPage'
import ToolsPage from './pages/admin/ToolsPage'
import UsersPage from './pages/admin/UsersPage'
import ChatPage from './pages/Chat'
import KbDetailPage from './pages/kb/KbDetailPage'
import KbListPage from './pages/kb/KbListPage'
import LoginPage from './pages/Login'
import { useAuthStore } from './stores/auth'
import { lightTheme } from './theme'

/** 路由守卫：无 token 一律回登录页；有 token 则后台拉取用户信息 */
function RequireAuth() {
  const bootstrap = useAuthStore((s) => s.bootstrap)
  const hasToken = Boolean(getAccessToken())

  useEffect(() => {
    if (hasToken) void bootstrap()
  }, [hasToken, bootstrap])

  if (!hasToken) return <Navigate to="/login" replace />
  return <Outlet />
}

/** 管理后台守卫：等待用户信息就绪后校验角色，非 ADMIN 重定向首页 */
function RequireAdmin() {
  const user = useAuthStore((s) => s.user)
  const status = useAuthStore((s) => s.status)

  if (status !== 'ready') {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
        <Spin />
      </div>
    )
  }
  if (user?.role !== 'ADMIN') return <Navigate to="/" replace />
  return <Outlet />
}

export default function App() {
  return (
    <ConfigProvider locale={zhCN} theme={lightTheme}>
      <AntdApp message={{ top: 14, duration: 2.5, maxCount: 3 }}>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<RequireAuth />}>
              <Route path="/" element={<ChatPage />} />
              <Route path="/c/:conversationId" element={<ChatPage />} />
              <Route path="/kb" element={<KbListPage />} />
              <Route path="/kb/:kbId" element={<KbDetailPage />} />
              <Route element={<RequireAdmin />}>
                <Route path="/admin" element={<AdminLayout />}>
                  <Route index element={<Navigate to="models" replace />} />
                  <Route path="models" element={<ModelsPage />} />
                  <Route path="mcp" element={<McpPage />} />
                  <Route path="tools" element={<ToolsPage />} />
                  <Route path="agents" element={<AgentsPage />} />
                  <Route path="users" element={<UsersPage />} />
                  <Route path="stats" element={<StatsPage />} />
                  <Route path="*" element={<Navigate to="models" replace />} />
                </Route>
              </Route>
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AntdApp>
    </ConfigProvider>
  )
}

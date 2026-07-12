import { App as AntdApp, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { useEffect } from 'react'
import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { getAccessToken } from './api/tokens'
import ChatPage from './pages/Chat'
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

export default function App() {
  return (
    <ConfigProvider locale={zhCN} theme={lightTheme}>
      <AntdApp>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<RequireAuth />}>
              <Route path="/" element={<ChatPage />} />
              <Route path="/c/:conversationId" element={<ChatPage />} />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AntdApp>
    </ConfigProvider>
  )
}

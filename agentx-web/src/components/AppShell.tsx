import { MenuUnfoldOutlined } from '@ant-design/icons'
import { Drawer } from 'antd'
import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { useIsMobile } from '../hooks/useIsMobile'
import { useChatStore } from '../stores/chat'
import Sidebar from './Sidebar'

interface AppShellProps {
  /** 顶栏标题（字符串或自定义节点，如带返回按钮） */
  title: ReactNode
  /** 顶栏右侧操作区 */
  extra?: ReactNode
  children: ReactNode
}

/**
 * 知识库 / 管理后台等非对话页的通用骨架：
 * 复用深色 Sidebar（含会话列表与导航入口），主区为可滚动内容页。
 */
export default function AppShell({ title, extra, children }: AppShellProps) {
  const isMobile = useIsMobile()
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [drawerOpen, setDrawerOpen] = useState(false)

  // 直接进入 /kb、/admin 时侧栏会话列表也保持可用
  const loadConversations = useChatStore((s) => s.loadConversations)
  useEffect(() => {
    void loadConversations()
  }, [loadConversations])

  const sidebarVisible = !isMobile && sidebarOpen

  return (
    <div className="ax-app">
      {isMobile ? (
        <Drawer
          placement="left"
          width={280}
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          closeIcon={null}
          styles={{ body: { padding: 0, display: 'flex' } }}
        >
          <Sidebar
            style={{ width: '100%' }}
            onCollapse={() => setDrawerOpen(false)}
            onNavigate={() => setDrawerOpen(false)}
          />
        </Drawer>
      ) : (
        <Sidebar hidden={!sidebarOpen} onCollapse={() => setSidebarOpen(false)} />
      )}

      <main className="ax-main">
        <div className="ax-topbar">
          {!sidebarVisible && (
            <button
              type="button"
              className="ax-icon-btn"
              aria-label="展开侧栏"
              onClick={() => (isMobile ? setDrawerOpen(true) : setSidebarOpen(true))}
            >
              <MenuUnfoldOutlined />
            </button>
          )}
          <span className="ax-topbar-title">{title}</span>
          {extra && <div className="ax-topbar-extra">{extra}</div>}
        </div>
        <div className="ax-page ax-scroll">{children}</div>
      </main>
    </div>
  )
}

import { PanelLeftOpen } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Sheet, SheetContent } from '@/components/ui/sheet'
import { useIsMobile } from '../hooks/useIsMobile'
import { useToastAnchor } from '../hooks/useToastAnchor'
import { useChatStore } from '../stores/chat'
import Sidebar from './Sidebar'

/** Drawer 内的侧栏是全宽面板，去掉卡片形态（原 .ant-drawer .ax-sidebar 规则） */
const DRAWER_SIDEBAR_STYLE = {
  width: '100%',
  height: '100%',
  border: 'none',
  borderRadius: 0,
  boxShadow: 'none',
} as const

interface AppShellProps {
  /** 顶栏标题（字符串或自定义节点，如带返回按钮） */
  title: ReactNode
  /** 顶栏右侧操作区 */
  extra?: ReactNode
  /** 内容区去掉默认内边距（如管理后台通高左轨布局） */
  flush?: boolean
  children: ReactNode
}

/**
 * 知识库 / 管理后台等非对话页的通用骨架：
 * 复用 Sidebar（含会话列表与导航入口），主区为可滚动内容页。
 */
export default function AppShell({ title, extra, flush, children }: AppShellProps) {
  const isMobile = useIsMobile()
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const mainRef = useRef<HTMLElement>(null)
  useToastAnchor(mainRef) // toast 居中于内容区（.ax-main），随侧栏收放跟随

  // 直接进入 /kb、/admin 时侧栏会话列表也保持可用
  const loadConversations = useChatStore((s) => s.loadConversations)
  useEffect(() => {
    void loadConversations()
  }, [loadConversations])

  const sidebarVisible = !isMobile && sidebarOpen

  return (
    <div className="ax-app">
      {isMobile ? (
        <Sheet open={drawerOpen} onOpenChange={setDrawerOpen}>
          <SheetContent side="left" className="w-[280px] max-w-[280px] p-0 [&>button]:hidden">
            <Sidebar
              style={DRAWER_SIDEBAR_STYLE}
              onCollapse={() => setDrawerOpen(false)}
              onNavigate={() => setDrawerOpen(false)}
            />
          </SheetContent>
        </Sheet>
      ) : (
        <Sidebar hidden={!sidebarOpen} onCollapse={() => setSidebarOpen(false)} />
      )}

      <main className="ax-main" ref={mainRef}>
        <div className="ax-topbar">
          {!sidebarVisible && (
            <button
              type="button"
              className="ax-icon-btn"
              aria-label="展开侧栏"
              onClick={() => (isMobile ? setDrawerOpen(true) : setSidebarOpen(true))}
            >
              <PanelLeftOpen className="size-4" />
            </button>
          )}
          <span className="ax-topbar-title">{title}</span>
          {extra && <div className="ax-topbar-extra">{extra}</div>}
        </div>
        <div className={`ax-page ax-scroll${flush ? ' ax-page--flush' : ''}`}>{children}</div>
      </main>
    </div>
  )
}

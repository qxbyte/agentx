import { Boxes, Cloud, LayoutGrid, LineChart, Bot, Users } from 'lucide-react'
import type { ReactNode } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { cn } from '@/lib/utils'
import AppShell from '../../components/AppShell'

const MENU_ITEMS: { key: string; icon: ReactNode; label: string }[] = [
  { key: 'models', icon: <Boxes className="size-[15px]" />, label: '模型配置' },
  { key: 'mcp', icon: <Cloud className="size-[15px]" />, label: 'MCP 服务' },
  { key: 'tools', icon: <LayoutGrid className="size-[15px]" />, label: '工具目录' },
  { key: 'agents', icon: <Bot className="size-[15px]" />, label: 'Agent' },
  { key: 'users', icon: <Users className="size-[15px]" />, label: '用户' },
  { key: 'stats', icon: <LineChart className="size-[15px]" />, label: '用量统计' },
]

/** 管理后台骨架：AppShell 内嵌左侧菜单 + 子路由内容区 */
export default function AdminLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  // /admin/models → models；子路径缺省时由路由 index 重定向兜底
  const current = location.pathname.split('/')[2] ?? 'models'

  return (
    <AppShell title="管理后台" flush>
      <div className="ax-admin">
        <nav className="ax-admin-menu flex flex-col gap-1 p-2">
          {MENU_ITEMS.map((item) => {
            const active = item.key === current
            return (
              <button
                key={item.key}
                type="button"
                onClick={() => navigate(`/admin/${item.key}`)}
                className={cn(
                  'flex items-center gap-2.5 rounded-full px-3.5 py-2 text-left text-sm transition-colors',
                  active
                    ? 'bg-accent font-medium text-foreground'
                    : 'text-muted-foreground hover:bg-accent/60 hover:text-foreground',
                )}
              >
                {item.icon}
                <span>{item.label}</span>
              </button>
            )
          })}
        </nav>
        <div className="ax-admin-content">
          <Outlet />
        </div>
      </div>
    </AppShell>
  )
}

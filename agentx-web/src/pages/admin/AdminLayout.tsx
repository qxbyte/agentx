import {
  ApiOutlined,
  AppstoreOutlined,
  BarChartOutlined,
  CloudServerOutlined,
  RobotOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { Menu } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import AppShell from '../../components/AppShell'

const MENU_ITEMS = [
  { key: 'models', icon: <ApiOutlined />, label: '模型配置' },
  { key: 'mcp', icon: <CloudServerOutlined />, label: 'MCP 服务' },
  { key: 'tools', icon: <AppstoreOutlined />, label: '工具目录' },
  { key: 'agents', icon: <RobotOutlined />, label: 'Agent' },
  { key: 'users', icon: <TeamOutlined />, label: '用户' },
  { key: 'stats', icon: <BarChartOutlined />, label: '用量统计' },
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
        <Menu
          className="ax-admin-menu"
          mode="inline"
          selectedKeys={[current]}
          items={MENU_ITEMS}
          onClick={({ key }) => navigate(`/admin/${key}`)}
        />
        <div className="ax-admin-content">
          <Outlet />
        </div>
      </div>
    </AppShell>
  )
}

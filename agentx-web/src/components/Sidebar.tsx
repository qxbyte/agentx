import {
  DeleteOutlined,
  EditOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MoreOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import { App as AntdApp, Button, Dropdown, Input, Modal, Tooltip } from 'antd'
import { useState } from 'react'
import type { CSSProperties, MouseEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { extractErrorMessage } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'
import type { Conversation } from '../types'
import Logo from './Logo'

const ROLE_LABELS: Record<string, string> = {
  ADMIN: '管理员',
  USER: '成员',
}

interface SidebarProps {
  hidden?: boolean
  style?: CSSProperties
  /** 折叠按钮回调（桌面端收起侧栏 / 移动端关闭抽屉） */
  onCollapse: () => void
  /** 点击会话或新建对话后的回调（移动端用于关闭抽屉） */
  onNavigate?: () => void
}

export default function Sidebar({ hidden = false, style, onCollapse, onNavigate }: SidebarProps) {
  const navigate = useNavigate()
  const { message, modal } = AntdApp.useApp()

  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)

  const conversations = useChatStore((s) => s.conversations)
  const activeConversationId = useChatStore((s) => s.activeConversationId)
  const renameConversation = useChatStore((s) => s.renameConversation)
  const removeConversation = useChatStore((s) => s.removeConversation)
  const resetChat = useChatStore((s) => s.reset)

  const [renameTarget, setRenameTarget] = useState<Conversation | null>(null)
  const [renameValue, setRenameValue] = useState('')
  const [renaming, setRenaming] = useState(false)
  const [menuOpenId, setMenuOpenId] = useState<string | null>(null)

  const goConversation = (id: string) => {
    navigate(`/c/${id}`)
    onNavigate?.()
  }

  const handleNewChat = () => {
    navigate('/')
    onNavigate?.()
  }

  const handleRenameOk = async () => {
    if (!renameTarget) return
    const title = renameValue.trim()
    if (!title) {
      message.warning('标题不能为空')
      return
    }
    setRenaming(true)
    try {
      await renameConversation(renameTarget.id, title)
      setRenameTarget(null)
    } catch (error) {
      message.error(extractErrorMessage(error, '重命名失败'))
    } finally {
      setRenaming(false)
    }
  }

  const confirmDelete = (conversation: Conversation) => {
    modal.confirm({
      title: '删除对话',
      content: `确定删除「${conversation.title || '未命名对话'}」吗？删除后不可恢复。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      async onOk() {
        try {
          const wasActive = await removeConversation(conversation.id)
          if (wasActive) navigate('/')
        } catch (error) {
          message.error(extractErrorMessage(error, '删除失败'))
        }
      },
    })
  }

  const handleLogout = () => {
    resetChat()
    logout()
    navigate('/login', { replace: true })
  }

  const stop = (event: MouseEvent) => event.stopPropagation()

  return (
    <aside
      className={`ax-sidebar${hidden ? ' ax-sidebar--hidden' : ''}`}
      style={style}
      aria-label="会话列表"
    >
      <div className="ax-sidebar-header">
        <Logo size={30} />
        <span className="ax-wordmark">AgentX</span>
        <Tooltip title="收起侧栏" placement="right">
          <button type="button" className="ax-icon-btn" onClick={onCollapse} aria-label="收起侧栏">
            <MenuFoldOutlined />
          </button>
        </Tooltip>
      </div>

      <div className="ax-new-chat">
        <Button icon={<PlusOutlined />} onClick={handleNewChat}>
          新建对话
        </Button>
      </div>

      <div className="ax-conv-list ax-scroll">
        {conversations.length > 0 && <div className="ax-conv-section">对话</div>}
        {conversations.length === 0 && <div className="ax-conv-empty">暂无对话，开始新对话吧</div>}
        {conversations.map((conversation) => {
          const active = conversation.id === activeConversationId
          return (
            <div
              key={conversation.id}
              className={`ax-conv-item${active ? ' ax-conv-item--active' : ''}`}
              onClick={() => goConversation(conversation.id)}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === 'Enter') goConversation(conversation.id)
              }}
            >
              <span className="ax-conv-title">{conversation.title || '未命名对话'}</span>
              <span
                className={`ax-conv-more${menuOpenId === conversation.id ? ' ax-conv-more--open' : ''}`}
                onClick={stop}
              >
                <Dropdown
                  trigger={['click']}
                  placement="bottomRight"
                  onOpenChange={(open) => setMenuOpenId(open ? conversation.id : null)}
                  menu={{
                    items: [
                      { key: 'rename', icon: <EditOutlined />, label: '重命名' },
                      { key: 'delete', icon: <DeleteOutlined />, label: '删除', danger: true },
                    ],
                    onClick: ({ key, domEvent }) => {
                      domEvent.stopPropagation()
                      if (key === 'rename') {
                        setRenameTarget(conversation)
                        setRenameValue(conversation.title)
                      } else if (key === 'delete') {
                        confirmDelete(conversation)
                      }
                    },
                  }}
                >
                  <button type="button" className="ax-icon-btn" aria-label="会话操作">
                    <MoreOutlined />
                  </button>
                </Dropdown>
              </span>
            </div>
          )
        })}
      </div>

      <div className="ax-sidebar-footer">
        <div className="ax-user-avatar">
          {(user?.nickname || user?.username || '?').slice(0, 1).toUpperCase()}
        </div>
        <div className="ax-user-meta">
          <div className="ax-user-name">{user?.nickname || user?.username || '未登录'}</div>
          <div className="ax-user-role">{user ? (ROLE_LABELS[user.role] ?? user.role) : ''}</div>
        </div>
        <Tooltip title="退出登录" placement="top">
          <button type="button" className="ax-icon-btn" onClick={handleLogout} aria-label="退出登录">
            <LogoutOutlined />
          </button>
        </Tooltip>
      </div>

      <Modal
        title="重命名对话"
        open={renameTarget !== null}
        confirmLoading={renaming}
        okText="保存"
        cancelText="取消"
        onOk={() => void handleRenameOk()}
        onCancel={() => setRenameTarget(null)}
        destroyOnHidden
      >
        <Input
          value={renameValue}
          onChange={(e) => setRenameValue(e.target.value)}
          placeholder="输入新标题"
          maxLength={60}
          onPressEnter={() => void handleRenameOk()}
          autoFocus
        />
      </Modal>
    </aside>
  )
}

import {
  BookOpen,
  Bot,
  FolderGit2,
  LogOut,
  MessageSquare,
  MoreHorizontal,
  PanelLeftClose,
  Pencil,
  Plus,
  Settings2,
  SquarePen,
  Trash2,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import type { CSSProperties, MouseEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import * as codingApi from '../api/coding'
import { Button } from '@/components/ui/button'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { extractErrorMessage } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'
import type { Conversation, Workspace } from '../types'
import WorkspaceFormDialog from './coding/WorkspaceFormDialog'
import Logo from './Logo'
import NewChatModal from './NewChatModal'

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
  const location = useLocation()

  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)
  const isAdmin = user?.role === 'ADMIN'

  const conversations = useChatStore((s) => s.conversations)
  const activeConversationId = useChatStore((s) => s.activeConversationId)
  const renameConversation = useChatStore((s) => s.renameConversation)
  const removeConversation = useChatStore((s) => s.removeConversation)
  const loadConversations = useChatStore((s) => s.loadConversations)
  const resetChat = useChatStore((s) => s.reset)
  const setWorkspaceId = useChatStore((s) => s.setWorkspaceId)
  const setKbIds = useChatStore((s) => s.setKbIds)

  const [projects, setProjects] = useState<Workspace[]>([])
  const refreshProjects = () => {
    void codingApi.listWorkspaces().then(setProjects).catch(() => setProjects([]))
  }
  useEffect(refreshProjects, [])

  const [renameTarget, setRenameTarget] = useState<Conversation | null>(null)
  const [renameValue, setRenameValue] = useState('')
  const [renaming, setRenaming] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<Conversation | null>(null)
  const [menuOpenId, setMenuOpenId] = useState<string | null>(null)
  const [newChatOpen, setNewChatOpen] = useState(false)
  /** 项目：新建/编辑对话框与删除确认 */
  const [projectDialogOpen, setProjectDialogOpen] = useState(false)
  const [editingProject, setEditingProject] = useState<Workspace | null>(null)
  const [deleteProjectTarget, setDeleteProjectTarget] = useState<Workspace | null>(null)

  const goConversation = (id: string) => {
    navigate(`/c/${id}`)
    onNavigate?.()
  }

  const handleNewChat = () => {
    navigate('/')
    onNavigate?.()
  }

  const handleConversationCreated = (id: string) => {
    void loadConversations()
    navigate(`/c/${id}`)
    onNavigate?.()
  }

  const isChatRoute = location.pathname === '/' || location.pathname.startsWith('/c/')
  const navItems = [
    { key: 'chat', to: '/', icon: <MessageSquare />, label: '对话', active: isChatRoute },
    {
      key: 'kb',
      to: '/kb',
      icon: <BookOpen />,
      label: '知识库',
      active: location.pathname.startsWith('/kb'),
    },
    {
      key: 'workspaces',
      to: '/workspaces',
      icon: <FolderGit2 />,
      label: '项目',
      active: location.pathname.startsWith('/workspaces'),
    },
    ...(isAdmin
      ? [
          {
            key: 'admin',
            to: '/admin',
            icon: <Settings2 />,
            label: '管理后台',
            active: location.pathname.startsWith('/admin'),
          },
        ]
      : []),
  ]

  const handleRenameOk = async () => {
    if (!renameTarget) return
    const title = renameValue.trim()
    if (!title) {
      toast.warning('标题不能为空')
      return
    }
    setRenaming(true)
    try {
      await renameConversation(renameTarget.id, title)
      setRenameTarget(null)
    } catch (error) {
      toast.error(extractErrorMessage(error, '重命名失败'))
    } finally {
      setRenaming(false)
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) return
    try {
      const wasActive = await removeConversation(deleteTarget.id)
      if (wasActive) navigate('/')
      setDeleteTarget(null)
    } catch (error) {
      toast.error(extractErrorMessage(error, '删除失败'))
    }
  }

  const handleLogout = () => {
    resetChat()
    logout()
    navigate('/login', { replace: true })
  }

  const stop = (event: MouseEvent) => event.stopPropagation()

  /** 在项目中新建对话：选中项目（预填其默认知识库）并回到新对话页 */
  const openProject = (ws: Workspace) => {
    setWorkspaceId(ws.id)
    setKbIds(ws.kbId ? [ws.kbId] : [])
    navigate('/')
    onNavigate?.()
  }

  const handleDeleteProject = async () => {
    if (!deleteProjectTarget) return
    try {
      await codingApi.deleteWorkspace(deleteProjectTarget.id)
      toast.success('项目已删除')
      setDeleteProjectTarget(null)
      refreshProjects()
    } catch (error) {
      toast.error(extractErrorMessage(error, '删除失败'))
    }
  }

  // 会话按项目归属分组：workspaceId 指向现存项目的挂到项目下，其余（含项目已删）归「对话」
  const projectIds = new Set(projects.map((p) => p.id))
  const grouped = new Map<string, Conversation[]>()
  const ungrouped: Conversation[] = []
  for (const c of conversations) {
    if (c.workspaceId && projectIds.has(c.workspaceId)) {
      const arr = grouped.get(c.workspaceId) ?? []
      arr.push(c)
      grouped.set(c.workspaceId, arr)
    } else {
      ungrouped.push(c)
    }
  }

  const renderConv = (conversation: Conversation) => {
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
          <DropdownMenu onOpenChange={(open) => setMenuOpenId(open ? conversation.id : null)}>
            <DropdownMenuTrigger asChild>
              <button type="button" className="ax-icon-btn" aria-label="会话操作">
                <MoreHorizontal className="size-4" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem
                onClick={() => {
                  setRenameTarget(conversation)
                  setRenameValue(conversation.title)
                }}
              >
                <Pencil className="size-4" />
                重命名
              </DropdownMenuItem>
              <DropdownMenuItem
                data-variant="destructive"
                onClick={() => setDeleteTarget(conversation)}
              >
                <Trash2 className="size-4" />
                删除
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </span>
      </div>
    )
  }

  return (
    <aside
      className={`ax-sidebar${hidden ? ' ax-sidebar--hidden' : ''}`}
      style={style}
      aria-label="会话列表"
    >
      <div className="ax-sidebar-header">
        <Logo size={30} />
        <span className="ax-wordmark">AgentX</span>
        <Tooltip>
          <TooltipTrigger asChild>
            <button type="button" className="ax-icon-btn" onClick={onCollapse} aria-label="收起侧栏">
              <PanelLeftClose className="size-4" />
            </button>
          </TooltipTrigger>
          <TooltipContent side="right">收起侧栏</TooltipContent>
        </Tooltip>
      </div>

      <div className="ax-new-chat">
        <Button
          variant="outline"
          onClick={handleNewChat}
          className="ax-new-chat-main h-[38px] flex-1 justify-start bg-background font-medium"
        >
          <Plus className="size-4" />
          新建对话
        </Button>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="outline"
              size="icon"
              className="ax-new-chat-agent h-[38px] w-[42px] bg-background"
              aria-label="选择 Agent 或知识库新建对话"
              onClick={() => setNewChatOpen(true)}
            >
              <Bot className="size-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent side="right">选择 Agent / 知识库新建</TooltipContent>
        </Tooltip>
      </div>

      <nav className="ax-nav" aria-label="主导航">
        {navItems.map((item) => (
          <button
            key={item.key}
            type="button"
            className={`ax-nav-item${item.active ? ' ax-nav-item--active' : ''}`}
            onClick={() => {
              navigate(item.to)
              onNavigate?.()
            }}
          >
            <span className="[&_svg]:size-[15px]">{item.icon}</span>
            <span>{item.label}</span>
          </button>
        ))}
      </nav>

      <div className="ax-conv-list ax-scroll">
        {/* 项目分组（ChatGPT Projects 式）：标题行 + 新建；行悬停出「新建对话 / 更多」 */}
        <div className="ax-conv-section flex items-center justify-between">
          <span>项目</span>
          <button
            type="button"
            aria-label="新建项目"
            title="新建项目"
            className="flex size-5 items-center justify-center rounded-full text-[var(--ax-text-secondary)] transition-colors hover:bg-[var(--ax-sidebar-hover)] hover:text-foreground"
            onClick={() => {
              setEditingProject(null)
              setProjectDialogOpen(true)
            }}
          >
            <Plus className="size-3.5" />
          </button>
        </div>
        {projects.length === 0 ? (
          <div className="ax-conv-empty">没有项目</div>
        ) : (
          projects.map((p) => {
            const convs = grouped.get(p.id) ?? []
            return (
              <div key={p.id} className="mb-1">
                <div
                  className="ax-conv-item group font-medium"
                  role="button"
                  tabIndex={0}
                  onClick={() => openProject(p)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') openProject(p)
                  }}
                  title={p.rootPath}
                >
                  <FolderGit2 className="size-[15px] shrink-0 text-[var(--ax-text-secondary)]" />
                  <span className="ax-conv-title">{p.name}</span>
                  <span
                    className="ml-auto flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100"
                    onClick={stop}
                  >
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <button
                          type="button"
                          className="ax-icon-btn !h-6 !w-6"
                          aria-label="在项目中新建对话"
                          onClick={() => openProject(p)}
                        >
                          <SquarePen className="size-3.5" />
                        </button>
                      </TooltipTrigger>
                      <TooltipContent>在项目中新建对话</TooltipContent>
                    </Tooltip>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <button type="button" className="ax-icon-btn !h-6 !w-6" aria-label="项目操作">
                          <MoreHorizontal className="size-3.5" />
                        </button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="start" className="min-w-[13rem]">
                        {/* 项目信息卡（Codex 式） */}
                        <div className="px-2 py-1.5">
                          <div className="flex items-center gap-2 text-sm font-medium">
                            <FolderGit2 className="size-4 shrink-0" />
                            <span className="truncate">{p.name}</span>
                          </div>
                          <div className="mt-1 flex items-center gap-1.5 text-xs text-muted-foreground">
                            <MessageSquare className="size-3" />
                            {convs.length} 个对话
                          </div>
                          <div className="mt-0.5 max-w-[220px] truncate font-mono text-[11px] text-muted-foreground">
                            {p.rootPath}
                          </div>
                        </div>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem onClick={() => openProject(p)}>
                          <SquarePen className="size-4" />
                          在项目中新建对话
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          onClick={() => {
                            setEditingProject(p)
                            setProjectDialogOpen(true)
                          }}
                        >
                          <Pencil className="size-4" />
                          编辑项目
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          data-variant="destructive"
                          onClick={() => setDeleteProjectTarget(p)}
                        >
                          <Trash2 className="size-4" />
                          删除项目
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </span>
                </div>
                <div className="ml-3 border-l border-[var(--ax-border-subtle)] pl-1">
                  {convs.map(renderConv)}
                </div>
              </div>
            )
          })
        )}

        {/* 未归属项目的对话 */}
        {ungrouped.length > 0 && <div className="ax-conv-section mt-2">对话</div>}
        {ungrouped.map(renderConv)}
      </div>

      <div className="ax-sidebar-footer">
        <div className="ax-user-avatar">
          {(user?.nickname || user?.username || '?').slice(0, 1).toUpperCase()}
        </div>
        <div className="ax-user-meta">
          <div className="ax-user-name">{user?.nickname || user?.username || '未登录'}</div>
          <div className="ax-user-role">{user ? (ROLE_LABELS[user.role] ?? user.role) : ''}</div>
        </div>
        <Tooltip>
          <TooltipTrigger asChild>
            <button type="button" className="ax-icon-btn" onClick={handleLogout} aria-label="退出登录">
              <LogOut className="size-4" />
            </button>
          </TooltipTrigger>
          <TooltipContent side="top">退出登录</TooltipContent>
        </Tooltip>
      </div>

      <Dialog open={renameTarget !== null} onOpenChange={(o) => !o && setRenameTarget(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>重命名对话</DialogTitle>
          </DialogHeader>
          <Input
            value={renameValue}
            onChange={(e) => setRenameValue(e.target.value)}
            placeholder="输入新标题"
            maxLength={60}
            autoFocus
            onKeyDown={(e) => {
              if (e.key === 'Enter') void handleRenameOk()
            }}
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => setRenameTarget(null)}>
              取消
            </Button>
            <Button onClick={() => void handleRenameOk()} disabled={renaming}>
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={deleteTarget !== null} onOpenChange={(o) => !o && setDeleteTarget(null)}>
        <AlertDialogContent className="max-w-md">
          <AlertDialogHeader>
            <AlertDialogTitle>删除对话</AlertDialogTitle>
            <AlertDialogDescription>
              确定删除「{deleteTarget?.title || '未命名对话'}」吗？删除后不可恢复。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => void handleDelete()}
            >
              删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <NewChatModal
        open={newChatOpen}
        onClose={() => setNewChatOpen(false)}
        onCreated={handleConversationCreated}
      />

      <WorkspaceFormDialog
        open={projectDialogOpen}
        onOpenChange={setProjectDialogOpen}
        editing={editingProject}
        onSaved={(ws) => {
          refreshProjects()
          if (!editingProject) openProject(ws)
        }}
      />

      <AlertDialog
        open={deleteProjectTarget !== null}
        onOpenChange={(o) => !o && setDeleteProjectTarget(null)}
      >
        <AlertDialogContent className="max-w-md">
          <AlertDialogHeader>
            <AlertDialogTitle>删除项目</AlertDialogTitle>
            <AlertDialogDescription>
              确定删除「{deleteProjectTarget?.name}」吗？仅解除绑定，不会删除磁盘上的代码目录；其下对话会移到「对话」区。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => void handleDeleteProject()}
            >
              删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </aside>
  )
}

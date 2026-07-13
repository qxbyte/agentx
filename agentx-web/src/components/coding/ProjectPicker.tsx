import { Check, ChevronDown, FolderGit2, FolderOpen, FolderPlus } from 'lucide-react'
import { useEffect, useState } from 'react'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'
import { useChatStore } from '../../stores/chat'
import type { Workspace } from '../../types'
import BlankProjectDialog from './BlankProjectDialog'
import WorkspaceFormDialog from './WorkspaceFormDialog'

/** 项目选择器（对标 Codex「选择项目」）：下拉列已有项目 + 内联「新建项目」。 */
export default function ProjectPicker() {
  const workspaceId = useChatStore((s) => s.workspaceId)
  const setWorkspaceId = useChatStore((s) => s.setWorkspaceId)
  const setKbIds = useChatStore((s) => s.setKbIds)
  const workspaces = useChatStore((s) => s.projects)
  const loadProjects = useChatStore((s) => s.loadProjects)

  const [dialogOpen, setDialogOpen] = useState(false)
  const [blankOpen, setBlankOpen] = useState(false)

  const load = () => void loadProjects()
  useEffect(load, [loadProjects])

  const current = workspaces.find((w) => w.id === workspaceId) ?? null
  const coding = workspaceId !== null

  /** 选中项目：进 coding 模式，并用该项目的默认知识库预填输入框知识库选择 */
  const selectProject = (ws: Workspace) => {
    setWorkspaceId(ws.id)
    setKbIds(ws.kbId ? [ws.kbId] : [])
  }

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button
            type="button"
            className={cn(
              'flex h-7 items-center gap-1.5 rounded-full px-2.5 text-xs transition-colors hover:bg-black/[0.06]',
              coding ? 'font-medium text-foreground' : 'text-[var(--ax-text-secondary)]',
            )}
          >
            <FolderGit2 className="size-3.5" />
            <span className="max-w-[160px] truncate">{current ? current.name : '选择项目'}</span>
            <ChevronDown className="size-3 opacity-50" />
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" className="min-w-[13rem]">
          <DropdownMenuItem onClick={() => setWorkspaceId(null)}>
            <span className={cn('size-4', workspaceId === null ? 'opacity-100' : 'opacity-0')}>
              <Check className="size-4" />
            </span>
            不绑定项目（普通对话）
          </DropdownMenuItem>
          {workspaces.map((ws) => (
            <DropdownMenuItem key={ws.id} onClick={() => selectProject(ws)}>
              <span className={cn('size-4', ws.id === workspaceId ? 'opacity-100' : 'opacity-0')}>
                <Check className="size-4" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate">{ws.name}</span>
                <span className="block truncate font-mono text-[10px] text-muted-foreground">
                  {ws.rootPath}
                </span>
              </span>
            </DropdownMenuItem>
          ))}
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={() => setBlankOpen(true)}>
            <FolderPlus className="size-4" />
            新建空白项目
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => setDialogOpen(true)}>
            <FolderOpen className="size-4" />
            使用现有文件夹
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <WorkspaceFormDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        onSaved={(ws) => {
          load()
          selectProject(ws)
        }}
      />

      <BlankProjectDialog
        open={blankOpen}
        onOpenChange={setBlankOpen}
        onCreated={(ws) => {
          load()
          selectProject(ws)
        }}
      />
    </>
  )
}

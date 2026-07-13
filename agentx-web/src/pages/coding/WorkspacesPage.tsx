import { FolderGit2, Pencil, Plus, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { MouseEvent } from 'react'
import { toast } from 'sonner'
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
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { extractErrorMessage } from '../../api/http'
import * as codingApi from '../../api/coding'
import * as kbApi from '../../api/kb'
import AppShell from '../../components/AppShell'
import WorkspaceFormDialog from '../../components/coding/WorkspaceFormDialog'
import ErrorState from '../../components/ErrorState'
import type { KnowledgeBase, Workspace } from '../../types'

export default function WorkspacesPage() {
  const [workspaces, setWorkspaces] = useState<Workspace[]>([])
  const [kbs, setKbs] = useState<KnowledgeBase[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Workspace | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Workspace | null>(null)

  const refresh = async () => {
    try {
      setWorkspaces(await codingApi.listWorkspaces())
      setLoadError(null)
    } catch (error) {
      setLoadError(extractErrorMessage(error, '加载项目失败'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refresh()
    void kbApi.listKbs().then(setKbs).catch(() => setKbs([]))
  }, [])

  const kbNameById = new Map(kbs.map((kb) => [kb.id, kb.name]))

  const openCreate = () => {
    setEditing(null)
    setModalOpen(true)
  }

  const openEdit = (ws: Workspace, event: MouseEvent) => {
    event.stopPropagation()
    setEditing(ws)
    setModalOpen(true)
  }

  const handleDelete = async () => {
    if (!deleteTarget) return
    try {
      await codingApi.deleteWorkspace(deleteTarget.id)
      toast.success('已删除')
      setDeleteTarget(null)
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, '删除失败'))
    }
  }

  return (
    <AppShell
      title="项目"
      extra={
        <Button onClick={openCreate}>
          <Plus className="size-4" />
          新建项目
        </Button>
      }
    >
      {loading ? (
        <div className="ax-kb-grid" aria-busy="true">
          {[0, 1, 2].map((i) => (
            <div key={i} className="ax-kb-card" style={{ cursor: 'default' }}>
              <div className="flex animate-pulse flex-col gap-3">
                <div className="h-4 w-1/2 rounded bg-muted" />
                <div className="h-3 w-full rounded bg-muted" />
              </div>
            </div>
          ))}
        </div>
      ) : loadError ? (
        <ErrorState
          message={loadError}
          onRetry={() => {
            setLoading(true)
            void refresh()
          }}
        />
      ) : workspaces.length === 0 ? (
        <div className="ax-page-empty flex flex-col items-center gap-4 text-center">
          <FolderGit2 className="size-9 text-muted-foreground/50" />
          <p className="text-sm text-muted-foreground">
            还没有项目，绑定一个本地代码目录，让智能体基于知识库改 bug
          </p>
          <Button onClick={openCreate}>
            <Plus className="size-4" />
            新建项目
          </Button>
        </div>
      ) : (
        <div className="ax-kb-grid">
          {workspaces.map((ws) => (
            <div key={ws.id} className="ax-kb-card" style={{ cursor: 'default' }}>
              <div className="ax-kb-card-head">
                <span className="ax-kb-card-icon">
                  <FolderGit2 className="size-[17px]" />
                </span>
                <span className="ax-kb-card-name">{ws.name}</span>
              </div>
              <p className="truncate font-mono text-xs text-muted-foreground">{ws.rootPath}</p>
              <div className="ax-kb-card-meta">
                {ws.kbId ? (
                  <Badge variant="outline">{kbNameById.get(ws.kbId) ?? '知识库'}</Badge>
                ) : (
                  <span>未绑定知识库</span>
                )}
                <span className="ax-kb-card-actions" onClick={(e) => e.stopPropagation()}>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="size-7"
                    aria-label="编辑"
                    onClick={(e) => openEdit(ws, e)}
                  >
                    <Pencil className="size-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="size-7 text-destructive hover:text-destructive"
                    aria-label="删除"
                    onClick={() => setDeleteTarget(ws)}
                  >
                    <Trash2 className="size-4" />
                  </Button>
                </span>
              </div>
            </div>
          ))}
        </div>
      )}

      <WorkspaceFormDialog
        open={modalOpen}
        onOpenChange={setModalOpen}
        editing={editing}
        onSaved={() => void refresh()}
      />

      <AlertDialog open={deleteTarget !== null} onOpenChange={(o) => !o && setDeleteTarget(null)}>
        <AlertDialogContent className="max-w-md">
          <AlertDialogHeader>
            <AlertDialogTitle>删除项目</AlertDialogTitle>
            <AlertDialogDescription>
              确定删除「{deleteTarget?.name}」吗？仅解除绑定，不会删除磁盘上的代码目录。
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
    </AppShell>
  )
}

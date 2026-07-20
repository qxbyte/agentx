import { BookOpen, Loader2, Pencil, Plus, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { MouseEvent } from 'react'
import { useNavigate } from 'react-router-dom'
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
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { extractErrorMessage } from '../../api/http'
import * as kbApi from '../../api/kb'
import AppShell from '../../components/AppShell'
import ErrorState from '../../components/ErrorState'
import type { KnowledgeBase } from '../../types'
import KbConfigFormItems, {
  KB_FORM_DEFAULTS,
  kbToFormState,
  validateKbForm,
  type KbFormErrors,
  type KbFormState,
} from './KbConfigFormItems'

export default function KbListPage() {
  const navigate = useNavigate()

  const [kbs, setKbs] = useState<KnowledgeBase[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<KnowledgeBase | null>(null)
  const [saving, setSaving] = useState(false)
  const [values, setValues] = useState<KbFormState>(KB_FORM_DEFAULTS)
  const [errors, setErrors] = useState<KbFormErrors>({})
  const [deleteTarget, setDeleteTarget] = useState<KnowledgeBase | null>(null)

  const patch = (p: Partial<KbFormState>) => setValues((prev) => ({ ...prev, ...p }))

  const refresh = async () => {
    try {
      setKbs(await kbApi.listKbs())
      setLoadError(null)
    } catch (error) {
      setLoadError(extractErrorMessage(error, '加载知识库失败'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refresh()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    setEditing(null)
    setValues(KB_FORM_DEFAULTS)
    setErrors({})
    setModalOpen(true)
  }

  const openEdit = (kb: KnowledgeBase, event: MouseEvent) => {
    event.stopPropagation()
    setEditing(kb)
    setValues(kbToFormState(kb))
    setErrors({})
    setModalOpen(true)
  }

  const handleSave = async () => {
    const result = validateKbForm(values)
    if (!result.ok) {
      setErrors(result.errors)
      return
    }
    setSaving(true)
    try {
      if (editing) {
        await kbApi.updateKb(editing.id, result.payload)
        toast.success('知识库已更新')
      } else {
        await kbApi.createKb(result.payload)
        toast.success('知识库已创建')
      }
      setModalOpen(false)
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) return
    try {
      await kbApi.deleteKb(deleteTarget.id)
      toast.success('已删除')
      setDeleteTarget(null)
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, '删除失败'))
    }
  }

  return (
    <AppShell
      title="知识库"
      extra={
        <Button onClick={openCreate}>
          <Plus className="size-4" />
          新建知识库
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
                <div className="h-3 w-2/3 rounded bg-muted" />
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
      ) : kbs.length === 0 ? (
        <div className="ax-page-empty flex flex-col items-center gap-4 text-center">
          <BookOpen className="size-9 text-muted-foreground/50" />
          <p className="text-sm text-muted-foreground">
            还没有知识库，创建第一个开始沉淀团队知识
          </p>
          <Button onClick={openCreate}>
            <Plus className="size-4" />
            新建知识库
          </Button>
        </div>
      ) : (
        <div className="ax-kb-grid">
          {kbs.map((kb) => (
            <div
              key={kb.id}
              className="ax-kb-card"
              role="button"
              tabIndex={0}
              onClick={() => navigate(`/kb/${kb.id}`)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') navigate(`/kb/${kb.id}`)
              }}
            >
              <div className="ax-kb-card-head">
                <span className="ax-kb-card-icon">
                  <BookOpen className="size-[17px]" />
                </span>
                <span className="ax-kb-card-name">{kb.name}</span>
              </div>
              <p className="ax-kb-card-desc">{kb.description || '暂无描述'}</p>
              <div className="ax-kb-card-meta">
                <span>{kb.createdAt ? `创建于 ${kb.createdAt.slice(0, 10)}` : ''}</span>
                <span className="ax-kb-card-actions" onClick={(e) => e.stopPropagation()}>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="size-7 text-[var(--ax-ios-blue)] hover:text-[var(--ax-ios-blue)]"
                    aria-label="编辑知识库"
                    onClick={(e) => openEdit(kb, e)}
                  >
                    <Pencil className="size-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="size-7 text-destructive hover:text-destructive"
                    aria-label="删除知识库"
                    onClick={(e) => {
                      e.stopPropagation()
                      setDeleteTarget(kb)
                    }}
                  >
                    <Trash2 className="size-4" />
                  </Button>
                </span>
              </div>
            </div>
          ))}
        </div>
      )}

      <Dialog open={modalOpen} onOpenChange={setModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing ? '编辑知识库' : '新建知识库'}</DialogTitle>
          </DialogHeader>
          <div className="mt-1">
            <KbConfigFormItems values={values} errors={errors} onChange={patch} />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setModalOpen(false)}>
              取消
            </Button>
            <Button onClick={() => void handleSave()} disabled={saving}>
              {saving && <Loader2 className="size-4 animate-spin" />}
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={deleteTarget !== null} onOpenChange={(o) => !o && setDeleteTarget(null)}>
        <AlertDialogContent className="max-w-md">
          <AlertDialogHeader>
            <AlertDialogTitle>删除知识库</AlertDialogTitle>
            <AlertDialogDescription>
              确定删除「{deleteTarget?.name}」吗？其中的文档与分段将一并删除，不可恢复。
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

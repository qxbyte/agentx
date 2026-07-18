import { ArrowUp, Check, Folder, Loader2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { extractErrorMessage } from '../../api/http'
import * as codingApi from '../../api/coding'
import type { DirListing } from '../../api/coding'

interface DirectoryPickerProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 初始定位目录(空则家目录) */
  initialPath?: string
  onSelect: (path: string) => void
}

/** 本机目录选择器:后端受限列举(家目录内),逐级导航,选定当前目录。 */
export default function DirectoryPicker({
  open,
  onOpenChange,
  initialPath,
  onSelect,
}: DirectoryPickerProps) {
  const [listing, setListing] = useState<DirListing | null>(null)
  const [loading, setLoading] = useState(false)

  const load = async (path?: string) => {
    setLoading(true)
    try {
      setListing(await codingApi.browseDirs(path))
    } catch (error) {
      toast.error(extractErrorMessage(error, '目录读取失败'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (open) void load(initialPath || undefined)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[560px]">
        <DialogHeader>
          <DialogTitle>选择本机目录</DialogTitle>
        </DialogHeader>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="icon"
            className="size-8 shrink-0"
            disabled={!listing?.parent || loading}
            onClick={() => void load(listing?.parent ?? undefined)}
            title="上一级"
          >
            <ArrowUp className="size-4" />
          </Button>
          <div className="min-w-0 flex-1 truncate rounded-lg bg-muted/50 px-3 py-1.5 font-mono text-xs text-foreground">
            {listing?.path ?? '…'}
          </div>
        </div>
        <div className="ax-scroll h-[300px] overflow-y-auto rounded-lg border border-[var(--ax-border)] p-1">
          {loading ? (
            <div className="flex h-full items-center justify-center">
              <Loader2 className="size-5 animate-spin text-muted-foreground" />
            </div>
          ) : listing && listing.dirs.length === 0 ? (
            <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
              没有子目录(可直接选定当前目录)
            </div>
          ) : (
            listing?.dirs.map((d) => (
              <button
                key={d.path}
                type="button"
                className="flex w-full items-center gap-2 rounded-md px-2.5 py-1.5 text-left text-[13px] transition-colors hover:bg-accent"
                onDoubleClick={() => void load(d.path)}
                onClick={() => void load(d.path)}
              >
                <Folder className="size-4 shrink-0 text-[var(--ax-text-secondary)]" />
                <span className="truncate">{d.name}</span>
              </button>
            ))
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button
            disabled={!listing || loading}
            onClick={() => {
              if (listing) {
                onSelect(listing.path)
                onOpenChange(false)
              }
            }}
          >
            <Check className="size-4" />
            选定当前目录
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

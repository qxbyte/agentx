import { Loader2, Plus } from 'lucide-react'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import * as adminApi from '../../api/admin'
import { extractErrorMessage } from '../../api/http'
import ErrorState from '../../components/ErrorState'
import PageHeader from '../../components/PageHeader'
import { useAuthStore } from '../../stores/auth'
import type { AdminUser, AdminUserPayload } from '../../types'

interface UserForm {
  username: string
  password: string
  nickname: string
  role: 'USER' | 'ADMIN'
}

const EMPTY_FORM: UserForm = { username: '', password: '', nickname: '', role: 'USER' }

export default function UsersPage() {
  const currentUser = useAuthStore((s) => s.user)

  const [users, setUsers] = useState<AdminUser[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState<UserForm>(EMPTY_FORM)
  const [errors, setErrors] = useState<Partial<Record<keyof UserForm, string>>>({})

  const patch = (p: Partial<UserForm>) => setForm((prev) => ({ ...prev, ...p }))

  const refresh = async () => {
    try {
      setUsers(await adminApi.listUsers())
      setLoadError(null)
    } catch (error) {
      setLoadError(extractErrorMessage(error, '加载用户列表失败'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refresh()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    setForm(EMPTY_FORM)
    setErrors({})
    setModalOpen(true)
  }

  const handleSave = async () => {
    const next: Partial<Record<keyof UserForm, string>> = {}
    if (!form.username.trim()) next.username = '请输入用户名'
    if (!form.password) next.password = '请输入初始密码'
    else if (form.password.length < 6) next.password = '至少 6 位'
    if (!form.nickname.trim()) next.nickname = '请输入昵称'
    if (Object.keys(next).length > 0) {
      setErrors(next)
      return
    }
    setSaving(true)
    try {
      await adminApi.createUser(form as AdminUserPayload)
      toast.success('用户已创建')
      setModalOpen(false)
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, '创建失败'))
    } finally {
      setSaving(false)
    }
  }

  const handleToggleStatus = async (user: AdminUser, active: boolean) => {
    const next = active ? 'ACTIVE' : 'DISABLED'
    setUsers((prev) => prev.map((u) => (u.id === user.id ? { ...u, status: next } : u)))
    try {
      await adminApi.updateUserStatus(user.id, next)
    } catch (error) {
      setUsers((prev) => prev.map((u) => (u.id === user.id ? { ...u, status: user.status } : u)))
      toast.error(extractErrorMessage(error, '操作失败'))
    }
  }

  return (
    <div>
      <PageHeader
        title="用户"
        description="管理平台成员账号、角色与启用状态"
        extra={
          <Button onClick={openCreate}>
            <Plus className="size-4" />
            新建用户
          </Button>
        }
      />

      {loadError ? (
        <ErrorState
          message={loadError}
          onRetry={() => {
            setLoading(true)
            void refresh()
          }}
        />
      ) : loading ? (
        <div className="flex animate-pulse flex-col gap-3 py-6">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-8 w-full rounded bg-muted" />
          ))}
        </div>
      ) : users.length === 0 ? (
        <div className="flex flex-col items-center gap-4 py-16 text-center">
          <p className="text-sm text-muted-foreground">还没有其他成员，创建账号邀请团队使用</p>
          <Button onClick={openCreate}>
            <Plus className="size-4" />
            新建用户
          </Button>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>用户名</TableHead>
              <TableHead>昵称</TableHead>
              <TableHead className="w-[110px]">角色</TableHead>
              <TableHead className="w-[130px]">创建时间</TableHead>
              <TableHead className="w-[110px]">状态</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {users.map((user) => (
              <TableRow key={user.id}>
                <TableCell className="max-w-0 truncate">{user.username}</TableCell>
                <TableCell className="max-w-0 truncate">{user.nickname}</TableCell>
                <TableCell>
                  <Badge variant={user.role === 'ADMIN' ? 'info' : 'default'}>
                    {user.role === 'ADMIN' ? '管理员' : '成员'}
                  </Badge>
                </TableCell>
                <TableCell>{user.createdAt ? user.createdAt.slice(0, 10) : '—'}</TableCell>
                <TableCell>
                  <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                    <Switch
                      checked={user.status === 'ACTIVE'}
                      disabled={user.id === currentUser?.id}
                      onCheckedChange={(checked) => void handleToggleStatus(user, checked)}
                    />
                    {user.status === 'ACTIVE' ? '启用' : '停用'}
                  </span>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <Dialog open={modalOpen} onOpenChange={setModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>新建用户</DialogTitle>
          </DialogHeader>
          <div className="mt-1 flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="u-username">用户名</Label>
              <Input
                id="u-username"
                placeholder="登录账号"
                maxLength={40}
                autoComplete="off"
                value={form.username}
                onChange={(e) => patch({ username: e.target.value })}
              />
              {errors.username && <p className="text-xs text-destructive">{errors.username}</p>}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="u-password">初始密码</Label>
              <Input
                id="u-password"
                type="password"
                placeholder="至少 6 位"
                autoComplete="new-password"
                value={form.password}
                onChange={(e) => patch({ password: e.target.value })}
              />
              {errors.password && <p className="text-xs text-destructive">{errors.password}</p>}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="u-nickname">昵称</Label>
              <Input
                id="u-nickname"
                placeholder="显示名称"
                maxLength={40}
                value={form.nickname}
                onChange={(e) => patch({ nickname: e.target.value })}
              />
              {errors.nickname && <p className="text-xs text-destructive">{errors.nickname}</p>}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>角色</Label>
              <Select value={form.role} onValueChange={(v) => patch({ role: v as UserForm['role'] })}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="USER">成员</SelectItem>
                  <SelectItem value="ADMIN">管理员</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setModalOpen(false)}>
              取消
            </Button>
            <Button onClick={() => void handleSave()} disabled={saving}>
              {saving && <Loader2 className="size-4 animate-spin" />}
              创建
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

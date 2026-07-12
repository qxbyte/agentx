import { create } from 'zustand'
import * as authApi from '../api/auth'
import { clearTokens, getAccessToken, setTokens } from '../api/tokens'
import type { User } from '../types'

interface AuthState {
  user: User | null
  /** idle: 未初始化；loading: 正在拉取当前用户；ready: 初始化完成 */
  status: 'idle' | 'loading' | 'ready'
  login: (username: string, password: string) => Promise<void>
  logout: () => void
  /** 应用启动时调用：有 token 则拉取当前用户信息 */
  bootstrap: () => Promise<void>
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  status: 'idle',

  async login(username, password) {
    const result = await authApi.login(username, password)
    setTokens(result.accessToken, result.refreshToken)
    set({ user: result.user, status: 'ready' })
  },

  logout() {
    clearTokens()
    set({ user: null, status: 'ready' })
  },

  async bootstrap() {
    if (get().status !== 'idle') return
    if (!getAccessToken()) {
      set({ status: 'ready' })
      return
    }
    set({ status: 'loading' })
    try {
      const user = await authApi.fetchMe()
      set({ user, status: 'ready' })
    } catch {
      // 401 情况下拦截器已尝试 refresh 并在失败时跳转 /login
      set({ user: null, status: 'ready' })
    }
  },
}))

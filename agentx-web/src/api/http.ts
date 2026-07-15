import axios, { AxiosError } from 'axios'
import type { AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios'
import type { ApiEnvelope, LoginResult } from '../types'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from './tokens'

export const http = axios.create({
  baseURL: '/api',
  timeout: 30_000,
})

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 单飞（single-flight）刷新：并发 401 只发起一次 refresh 请求 */
let refreshPromise: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) throw new Error('缺少 refreshToken')
  // 用裸 axios，避免走本实例的拦截器造成递归
  const res = await axios.post<ApiEnvelope<LoginResult>>('/api/v1/auth/refresh', { refreshToken })
  if (res.data.code !== 0) throw new Error(res.data.message || '刷新令牌失败')
  const { accessToken, refreshToken: nextRefreshToken } = res.data.data
  setTokens(accessToken, nextRefreshToken)
  return accessToken
}

function redirectToLogin(): void {
  clearTokens()
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean }

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as RetriableConfig | undefined
    const status = error.response?.status
    const url = config?.url ?? ''
    const isAuthEndpoint = url.includes('/auth/login') || url.includes('/auth/refresh')

    if (status === 401 && config && !config._retried && !isAuthEndpoint) {
      config._retried = true
      try {
        refreshPromise = refreshPromise ?? refreshAccessToken()
        const token = await refreshPromise
        config.headers.Authorization = `Bearer ${token}`
        return http.request(config)
      } catch (refreshError) {
        redirectToLogin()
        return Promise.reject(refreshError)
      } finally {
        refreshPromise = null
      }
    }
    return Promise.reject(error)
  },
)

/** 发请求并拆信封：code !== 0 时抛出带后端 message 的错误 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiEnvelope<T>>(config)
  const envelope = response.data
  if (envelope.code !== 0) {
    throw new Error(envelope.message || '请求失败')
  }
  return envelope.data
}

/** HTTP 404 判定：目标资源不存在或已被处理（如审批项已落定） */
export function isNotFoundError(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 404
}

/** 从 axios / 普通异常中提取给用户看的文案 */
export function extractErrorMessage(error: unknown, fallback = '请求失败，请稍后重试'): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as Partial<ApiEnvelope<unknown>> | undefined
    if (data?.message) return data.message
    if (error.response?.status === 401) return '登录已过期，请重新登录'
    return error.message || fallback
  }
  if (error instanceof Error && error.message) return error.message
  return fallback
}

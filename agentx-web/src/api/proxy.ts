import { request } from './http'

export interface ProxyConfig {
  enabled: boolean
  host: string
  port: number | null
}

export interface ProxyTestResult {
  ok: boolean
  finalUrl?: string
  error?: string
}

export function getProxy(): Promise<ProxyConfig> {
  return request<ProxyConfig>({ url: '/v1/admin/proxy', method: 'GET' })
}

export function updateProxy(config: ProxyConfig): Promise<ProxyConfig> {
  return request<ProxyConfig>({ url: '/v1/admin/proxy', method: 'PUT', data: config })
}

export function testProxy(): Promise<ProxyTestResult> {
  return request<ProxyTestResult>({ url: '/v1/admin/proxy/test', method: 'POST' })
}

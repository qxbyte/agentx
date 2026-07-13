import type { ExternalKb, ExternalKbProbe } from '../types'
import { request } from './http'

export interface ExternalKbPayload {
  name: string
  baseUrl: string
  vaultId: string
  heartbeatPath?: string
  infoPath?: string
  searchPath?: string
  topK?: number
  similarityThreshold?: number
  enabled: boolean
}

export function listExternalKbs(): Promise<ExternalKb[]> {
  return request<ExternalKb[]>({ url: '/v1/admin/external-kbs', method: 'GET' })
}

/** 面向用户：启用中的外部库（输入框知识库选择器用，非 admin 接口） */
export function listEnabledExternalKbs(): Promise<ExternalKb[]> {
  return request<ExternalKb[]>({ url: '/v1/kb/external', method: 'GET' })
}

export function createExternalKb(payload: ExternalKbPayload): Promise<ExternalKb> {
  return request<ExternalKb>({ url: '/v1/admin/external-kbs', method: 'POST', data: payload })
}

export function updateExternalKb(id: string, payload: ExternalKbPayload): Promise<ExternalKb> {
  return request<ExternalKb>({ url: `/v1/admin/external-kbs/${id}`, method: 'PUT', data: payload })
}

export function deleteExternalKb(id: string): Promise<void> {
  return request<void>({ url: `/v1/admin/external-kbs/${id}`, method: 'DELETE' })
}

export interface VaultInfo {
  vaultId: string
  name: string
  docCount: number
  chunkCount: number
  embeddingModel?: string | null
  dims: number
}

/** 仓库发现：只填服务地址即可列出外部服务的全部可接入仓库 */
export function discoverVaults(baseUrl: string): Promise<VaultInfo[]> {
  return request<VaultInfo[]>({
    url: '/v1/admin/external-kbs/discover',
    method: 'POST',
    data: { baseUrl },
  })
}

/** 已存配置的连接测试 */
export function testExternalKb(id: string): Promise<ExternalKbProbe> {
  return request<ExternalKbProbe>({ url: `/v1/admin/external-kbs/${id}/test`, method: 'POST' })
}

/** 表单参数直接探测（保存前预检） */
export function probeExternalKb(payload: ExternalKbPayload): Promise<ExternalKbProbe> {
  return request<ExternalKbProbe>({ url: '/v1/admin/external-kbs/probe', method: 'POST', data: payload })
}

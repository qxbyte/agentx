import type {
  AdminUser,
  AdminUserPayload,
  AgentPayload,
  AgentView,
  DailyTokenStat,
  McpServer,
  McpToolPreview,
  McpTransport,
  ModelConfig,
  ModelConfigPayload,
  ModelTokenStat,
  TokenStatsSummary,
  UserStatus,
} from '../types'
import { request } from './http'

/* ---------- 模型配置 ---------- */

export function listModelConfigs(): Promise<ModelConfig[]> {
  return request<ModelConfig[]>({ url: '/v1/admin/model-configs', method: 'GET' })
}

export function createModelConfig(payload: ModelConfigPayload): Promise<ModelConfig> {
  return request<ModelConfig>({ url: '/v1/admin/model-configs', method: 'POST', data: payload })
}

export function updateModelConfig(id: string, payload: ModelConfigPayload): Promise<ModelConfig> {
  return request<ModelConfig>({
    url: `/v1/admin/model-configs/${id}`,
    method: 'PUT',
    data: payload,
  })
}

export function deleteModelConfig(id: string): Promise<void> {
  return request<void>({ url: `/v1/admin/model-configs/${id}`, method: 'DELETE' })
}

export function setDefaultModelConfig(id: string): Promise<void> {
  return request<void>({ url: `/v1/admin/model-configs/${id}/default`, method: 'PATCH' })
}

/* ---------- MCP 服务 ---------- */

export interface McpServerPayload {
  name: string
  transport: McpTransport
  /** JSON 字符串，与后端 View 存储格式一致 */
  connectParams: string
  enabled: boolean
}

export function listMcpServers(): Promise<McpServer[]> {
  return request<McpServer[]>({ url: '/v1/admin/mcp-servers', method: 'GET' })
}

export function createMcpServer(payload: McpServerPayload): Promise<McpServer> {
  return request<McpServer>({ url: '/v1/admin/mcp-servers', method: 'POST', data: payload })
}

export function updateMcpServer(id: string, payload: McpServerPayload): Promise<McpServer> {
  return request<McpServer>({ url: `/v1/admin/mcp-servers/${id}`, method: 'PUT', data: payload })
}

export function deleteMcpServer(id: string): Promise<void> {
  return request<void>({ url: `/v1/admin/mcp-servers/${id}`, method: 'DELETE' })
}

export function testMcpConnection(id: string): Promise<McpToolPreview[]> {
  return request<McpToolPreview[]>({
    url: `/v1/admin/mcp-servers/${id}/test-connection`,
    method: 'POST',
    // 拉起远程 MCP 进程/连接可能较慢
    timeout: 60_000,
  })
}

export function listMcpServerTools(id: string): Promise<McpToolPreview[]> {
  return request<McpToolPreview[]>({ url: `/v1/admin/mcp-servers/${id}/tools`, method: 'GET' })
}

/* ---------- 工具目录 ---------- */

export function toggleToolEnabled(name: string, value: boolean): Promise<void> {
  return request<void>({
    url: `/v1/admin/tools/${encodeURIComponent(name)}/enabled`,
    method: 'PATCH',
    params: { value },
  })
}

/* ---------- Agent 管理 ---------- */

export function listAdminAgents(): Promise<AgentView[]> {
  return request<AgentView[]>({ url: '/v1/admin/agents', method: 'GET' })
}

export function createAgent(payload: AgentPayload): Promise<AgentView> {
  return request<AgentView>({ url: '/v1/admin/agents', method: 'POST', data: payload })
}

export function updateAgent(id: string, payload: AgentPayload): Promise<AgentView> {
  return request<AgentView>({ url: `/v1/admin/agents/${id}`, method: 'PUT', data: payload })
}

export function deleteAgent(id: string): Promise<void> {
  return request<void>({ url: `/v1/admin/agents/${id}`, method: 'DELETE' })
}

/* ---------- 用户管理 ---------- */

export function listUsers(): Promise<AdminUser[]> {
  return request<AdminUser[]>({ url: '/v1/admin/users', method: 'GET' })
}

export function createUser(payload: AdminUserPayload): Promise<AdminUser> {
  return request<AdminUser>({ url: '/v1/admin/users', method: 'POST', data: payload })
}

export function updateUserStatus(id: string, value: UserStatus): Promise<void> {
  return request<void>({
    url: `/v1/admin/users/${id}/status`,
    method: 'PATCH',
    params: { value },
  })
}

/* ---------- 用量统计 ---------- */

export function fetchTokenSummary(): Promise<TokenStatsSummary> {
  return request<TokenStatsSummary>({ url: '/v1/admin/stats/tokens/summary', method: 'GET' })
}

export function fetchDailyTokenStats(days = 14): Promise<DailyTokenStat[]> {
  return request<DailyTokenStat[]>({
    url: '/v1/admin/stats/tokens/daily',
    method: 'GET',
    params: { days },
  })
}

export function fetchModelTokenStats(): Promise<ModelTokenStat[]> {
  return request<ModelTokenStat[]>({ url: '/v1/admin/stats/tokens/by-model', method: 'GET' })
}

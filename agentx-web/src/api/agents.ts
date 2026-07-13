import type { AgentView, ToolView } from '../types'
import { request } from './http'

/** 普通用户可见的 Agent 列表（新建对话时选择） */
export function listAgents(): Promise<AgentView[]> {
  return request<AgentView[]>({ url: '/v1/agents', method: 'GET' })
}

/** 工具目录（管理后台工具页 / Agent 表单多选共用） */
export function listTools(): Promise<ToolView[]> {
  return request<ToolView[]>({ url: '/v1/tools', method: 'GET' })
}

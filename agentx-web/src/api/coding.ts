import type { Workspace, WorkspaceValidation } from '../types'
import { request } from './http'

export interface WorkspacePayload {
  name: string
  rootPath: string
  kbId?: string | null
}

export function listWorkspaces(): Promise<Workspace[]> {
  return request<Workspace[]>({ url: '/v1/coding/workspaces', method: 'GET' })
}

export function createWorkspace(payload: WorkspacePayload): Promise<Workspace> {
  return request<Workspace>({ url: '/v1/coding/workspaces', method: 'POST', data: payload })
}

export function updateWorkspace(id: string, payload: WorkspacePayload): Promise<Workspace> {
  return request<Workspace>({ url: `/v1/coding/workspaces/${id}`, method: 'PUT', data: payload })
}

export function deleteWorkspace(id: string): Promise<void> {
  return request<void>({ url: `/v1/coding/workspaces/${id}`, method: 'DELETE' })
}

/** 新建空白项目：后端在受控根下建目录并 git init 后返回工作区 */
export function createBlankWorkspace(payload: { name: string; kbId?: string | null }): Promise<Workspace> {
  return request<Workspace>({ url: '/v1/coding/workspaces/blank', method: 'POST', data: payload })
}

/** 探测目录可用性（存在/可写/git 仓库） */
export function validateWorkspacePath(rootPath: string): Promise<WorkspaceValidation> {
  return request<WorkspaceValidation>({
    url: '/v1/coding/workspaces/validate',
    method: 'POST',
    data: { rootPath },
  })
}

/** Ask 审批回传：批准/拒绝一个待审批操作，解冻后端阻塞的工具线程 */
export function resolveApproval(approvalId: string, approved: boolean): Promise<void> {
  return request<void>({
    url: `/v1/chat/approvals/${approvalId}`,
    method: 'POST',
    data: { approved },
  })
}

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

/** 编码模式切换回传：轮内立即生效（切 AUTO 时后端自动批准全部未决审批） */
export function updateCodingMode(conversationId: string, mode: string): Promise<void> {
  return request<void>({
    url: `/v1/chat/conversations/${conversationId}/coding-mode`,
    method: 'PUT',
    data: { mode },
  })
}

/* ---------- 本机目录浏览(项目目录选择器) ---------- */

export interface DirListing {
  path: string
  parent: string | null
  dirs: { name: string; path: string }[]
}

export function browseDirs(path?: string): Promise<DirListing> {
  return request<DirListing>({
    url: '/v1/coding/fs/dirs',
    method: 'GET',
    ...(path ? { params: { path } } : {}),
  })
}

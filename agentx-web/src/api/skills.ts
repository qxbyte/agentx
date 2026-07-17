import type { SkillDetail, SkillMeta, SkillPayload, SkillView } from '../types'
import { request } from './http'

/** 启用中的元数据（/ 补全菜单，永不含 content） */
export function listSkillMenu(): Promise<SkillMeta[]> {
  return request<SkillMeta[]>({ url: '/v1/skills', method: 'GET' })
}

/** 管理列表（含停用） */
export function listSkills(): Promise<SkillView[]> {
  return request<SkillView[]>({ url: '/v1/skills/all', method: 'GET' })
}

/** 详情（含 content，编辑时按需拉取——渐进式披露 L2） */
export function getSkill(id: string): Promise<SkillDetail> {
  return request<SkillDetail>({ url: `/v1/skills/${id}`, method: 'GET' })
}

export function createSkill(payload: SkillPayload): Promise<SkillDetail> {
  return request<SkillDetail>({ url: '/v1/skills', method: 'POST', data: payload })
}

export function updateSkill(id: string, payload: SkillPayload): Promise<SkillDetail> {
  return request<SkillDetail>({ url: `/v1/skills/${id}`, method: 'PUT', data: payload })
}

export function setSkillEnabled(id: string, enabled: boolean): Promise<SkillView> {
  return request<SkillView>({
    url: `/v1/skills/${id}/enabled`,
    method: 'PATCH',
    data: { enabled },
  })
}

export function deleteSkill(id: string): Promise<void> {
  return request<void>({ url: `/v1/skills/${id}`, method: 'DELETE' })
}

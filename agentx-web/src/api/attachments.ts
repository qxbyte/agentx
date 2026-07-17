import type { AttachmentEntry } from '../lib/attachments'
import { request } from './http'

/** 与后端 AttachmentService.UploadResult 对应 */
export interface UploadResult {
  id: string | null
  filename: string
  relPath: string | null
  kind: string
  sizeBytes: number
  charCount: number
  truncated: boolean
  error: string | null
}

/** 批量上传附件（上传即解析）：失败项在结果里带 error，不抛异常 */
export async function uploadAttachments(entries: AttachmentEntry[]): Promise<UploadResult[]> {
  const form = new FormData()
  for (const entry of entries) {
    form.append('files', entry.file, entry.file.name)
    form.append('relPaths', entry.relPath ?? '')
  }
  return request<UploadResult[]>({
    method: 'POST',
    url: '/v1/attachments',
    data: form,
    // 大文件/批量解析耗时超默认 30s
    timeout: 120_000,
  })
}

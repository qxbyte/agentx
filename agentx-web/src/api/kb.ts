import type {
  DocView,
  HitTestResult,
  IngestTask,
  KbPayload,
  KnowledgeBase,
  SegmentView,
} from '../types'
import { request } from './http'

/* ---------- 知识库 CRUD ---------- */

export function listKbs(): Promise<KnowledgeBase[]> {
  return request<KnowledgeBase[]>({ url: '/v1/kb', method: 'GET' })
}

export function createKb(payload: KbPayload): Promise<KnowledgeBase> {
  return request<KnowledgeBase>({ url: '/v1/kb', method: 'POST', data: payload })
}

export function updateKb(id: string, payload: KbPayload): Promise<KnowledgeBase> {
  return request<KnowledgeBase>({ url: `/v1/kb/${id}`, method: 'PUT', data: payload })
}

export function deleteKb(id: string): Promise<void> {
  return request<void>({ url: `/v1/kb/${id}`, method: 'DELETE' })
}

/* ---------- 文档 ---------- */

export function uploadDocument(kbId: string, file: File): Promise<DocView> {
  const formData = new FormData()
  formData.append('file', file)
  return request<DocView>({
    url: `/v1/kb/${kbId}/documents`,
    method: 'POST',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    // 大文件上传不受默认 30s 限制
    timeout: 120_000,
  })
}

export function listDocuments(kbId: string): Promise<DocView[]> {
  return request<DocView[]>({ url: `/v1/kb/${kbId}/documents`, method: 'GET' })
}

export function deleteDocument(docId: string): Promise<void> {
  return request<void>({ url: `/v1/kb/documents/${docId}`, method: 'DELETE' })
}

export function reingestDocument(docId: string): Promise<void> {
  return request<void>({ url: `/v1/kb/documents/${docId}/reingest`, method: 'POST' })
}

export function fetchDocTask(docId: string): Promise<IngestTask> {
  return request<IngestTask>({ url: `/v1/kb/documents/${docId}/task`, method: 'GET' })
}

/* ---------- 分段 ---------- */

export function listSegments(docId: string): Promise<SegmentView[]> {
  return request<SegmentView[]>({ url: `/v1/kb/documents/${docId}/segments`, method: 'GET' })
}

export function updateSegment(segmentId: string, content: string): Promise<SegmentView> {
  return request<SegmentView>({
    url: `/v1/kb/segments/${segmentId}`,
    method: 'PUT',
    data: { content },
  })
}

export function toggleSegmentEnabled(segmentId: string, value: boolean): Promise<void> {
  return request<void>({
    url: `/v1/kb/segments/${segmentId}/enabled`,
    method: 'PATCH',
    params: { value },
  })
}

/* ---------- 命中测试 ---------- */

export function hitTest(
  kbId: string,
  payload: { query: string; topK?: number; similarityThreshold?: number },
): Promise<HitTestResult[]> {
  return request<HitTestResult[]>({
    url: `/v1/kb/${kbId}/hit-test`,
    method: 'POST',
    data: payload,
  })
}

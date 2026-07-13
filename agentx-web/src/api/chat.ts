import type { ChatMessage, Conversation } from '../types'
import { request } from './http'

export function listConversations(): Promise<Conversation[]> {
  return request<Conversation[]>({ url: '/v1/chat/conversations', method: 'GET' })
}

export function createConversation(payload?: {
  modelConfigId?: string
  agentId?: string
  kbIds?: string[]
}): Promise<Conversation> {
  return request<Conversation>({
    url: '/v1/chat/conversations',
    method: 'POST',
    data: payload ?? {},
  })
}

export function renameConversation(id: string, title: string): Promise<Conversation> {
  return request<Conversation>({
    url: `/v1/chat/conversations/${id}`,
    method: 'PATCH',
    data: { title },
  })
}

export function deleteConversation(id: string): Promise<void> {
  return request<void>({ url: `/v1/chat/conversations/${id}`, method: 'DELETE' })
}

export function listMessages(conversationId: string): Promise<ChatMessage[]> {
  return request<ChatMessage[]>({
    url: `/v1/chat/conversations/${conversationId}/messages`,
    method: 'GET',
  })
}

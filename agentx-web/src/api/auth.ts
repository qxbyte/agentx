import type { LoginResult, User } from '../types'
import { request } from './http'

export function login(username: string, password: string): Promise<LoginResult> {
  return request<LoginResult>({
    url: '/v1/auth/login',
    method: 'POST',
    data: { username, password },
  })
}

export function fetchMe(): Promise<User> {
  return request<User>({ url: '/v1/auth/me', method: 'GET' })
}

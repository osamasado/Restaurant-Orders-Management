import { apiFetch } from './http'
import type { StaffResponse } from './types'

export function login(name: string, pin: string): Promise<StaffResponse> {
  return apiFetch<StaffResponse>('/staff/login', { method: 'POST', body: { name, pin } })
}

export function me(): Promise<StaffResponse> {
  return apiFetch<StaffResponse>('/staff/me')
}

export function logout(): Promise<void> {
  return apiFetch<void>('/staff/logout', { method: 'POST' })
}

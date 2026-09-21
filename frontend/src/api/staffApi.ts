import { apiFetch } from './http'
import type { StaffAccountCreateRequest, StaffAccountUpdateRequest, StaffResponse } from './types'

export function login(name: string, pin: string): Promise<StaffResponse> {
  return apiFetch<StaffResponse>('/staff/login', { method: 'POST', body: { name, pin } })
}

export function me(): Promise<StaffResponse> {
  return apiFetch<StaffResponse>('/staff/me')
}

export function logout(): Promise<void> {
  return apiFetch<void>('/staff/logout', { method: 'POST' })
}

export function listAccounts(): Promise<StaffResponse[]> {
  return apiFetch('/staff/accounts')
}

export function createAccount(request: StaffAccountCreateRequest): Promise<StaffResponse> {
  return apiFetch('/staff/accounts', { method: 'POST', body: request })
}

export function updateAccount(id: number, request: StaffAccountUpdateRequest): Promise<StaffResponse> {
  return apiFetch(`/staff/accounts/${id}`, { method: 'PUT', body: request })
}

export function deleteAccount(id: number): Promise<void> {
  return apiFetch(`/staff/accounts/${id}`, { method: 'DELETE' })
}

export function resetPin(id: number, pin: string): Promise<StaffResponse> {
  return apiFetch(`/staff/accounts/${id}/reset-pin`, { method: 'POST', body: { pin } })
}

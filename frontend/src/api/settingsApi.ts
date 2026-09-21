import { apiFetch } from './http'
import type { ConfigRequest, ConfigResponse } from './types'

export function getSettings(): Promise<ConfigResponse> {
  return apiFetch('/settings')
}

export function updateSettings(request: ConfigRequest): Promise<ConfigResponse> {
  return apiFetch('/settings', { method: 'PUT', body: request })
}

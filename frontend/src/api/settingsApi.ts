import { apiFetch } from './http'
import type { ConfigRequest, ConfigResponse, OrderNumberStatus } from './types'

export function getSettings(): Promise<ConfigResponse> {
  return apiFetch('/settings')
}

export function updateSettings(request: ConfigRequest): Promise<ConfigResponse> {
  return apiFetch('/settings', { method: 'PUT', body: request })
}

export function getOrderNumberStatus(): Promise<OrderNumberStatus> {
  return apiFetch('/settings/order-number')
}

/** Restarts the displayed order number at 001. Answers 409 while any order is submitted, in preparation or ready. */
export function resetOrderNumber(): Promise<OrderNumberStatus> {
  return apiFetch('/settings/order-number/reset', { method: 'POST' })
}

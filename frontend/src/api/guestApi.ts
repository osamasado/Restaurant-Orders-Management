import { apiFetch } from './http'
import type {
  CartQuoteRequest,
  CartQuoteResponse,
  ConfigResponse,
  GuestCategoryResponse,
  GuestOrderRequest,
  GuestOrderResponse,
  GuestOrderStatusResponse,
  GuestTableResponse,
  Language,
} from './types'

export function getGuestMenu(language: Language): Promise<GuestCategoryResponse[]> {
  return apiFetch(`/guest/menu?language=${language}`)
}

export function getGuestSettings(): Promise<ConfigResponse> {
  return apiFetch('/guest/settings')
}

export function quoteCart(request: CartQuoteRequest): Promise<CartQuoteResponse> {
  return apiFetch('/guest/cart/quote', { method: 'POST', body: request })
}

export function claimDevice(code: string): Promise<GuestTableResponse> {
  return apiFetch('/guest/device/claim', { method: 'POST', body: { code } })
}

export function submitOrder(request: GuestOrderRequest): Promise<GuestOrderResponse> {
  return apiFetch('/guest/orders', { method: 'POST', body: request })
}

/**
 * What proves this guest may follow this order: the order's own secret (handed out when it was placed), or, for an
 * order at a table that was placed before the secret existed, the paired device's code.
 */
export type OrderCredentials = { token: string | null; deviceCode: string | null }

export function getOrderStatus(orderId: number, credentials: OrderCredentials): Promise<GuestOrderStatusResponse> {
  const headers: Record<string, string> = {}
  if (credentials.token) headers['X-Order-Token'] = credentials.token
  else if (credentials.deviceCode) headers['X-Device-Code'] = credentials.deviceCode
  return apiFetch(`/guest/orders/${orderId}`, { headers })
}

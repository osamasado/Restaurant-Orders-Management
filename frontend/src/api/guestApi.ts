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

export function getOrderStatus(orderId: number, deviceCode: string): Promise<GuestOrderStatusResponse> {
  return apiFetch(`/guest/orders/${orderId}`, { headers: { 'X-Device-Code': deviceCode } })
}

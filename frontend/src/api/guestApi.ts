import { apiFetch } from './http'
import type { CartQuoteRequest, CartQuoteResponse, ConfigResponse, GuestCategoryResponse, Language } from './types'

export function getGuestMenu(language: Language): Promise<GuestCategoryResponse[]> {
  return apiFetch(`/guest/menu?language=${language}`)
}

export function getGuestSettings(): Promise<ConfigResponse> {
  return apiFetch('/guest/settings')
}

export function quoteCart(request: CartQuoteRequest): Promise<CartQuoteResponse> {
  return apiFetch('/guest/cart/quote', { method: 'POST', body: request })
}

import { apiFetch } from './http'
import type { ConfigResponse, GuestCategoryResponse, Language } from './types'

export function getGuestMenu(language: Language): Promise<GuestCategoryResponse[]> {
  return apiFetch(`/guest/menu?language=${language}`)
}

export function getGuestSettings(): Promise<ConfigResponse> {
  return apiFetch('/guest/settings')
}

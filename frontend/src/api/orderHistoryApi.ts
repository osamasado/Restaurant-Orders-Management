import { apiFetch } from './http'
import type { OrderHistoryPage } from './types'

/** Newest orders first. orderNumber narrows the list to the order with that number. */
export function getOrderHistory(page: number, size: number, orderNumber?: number): Promise<OrderHistoryPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (orderNumber !== undefined) params.set('orderNumber', String(orderNumber))
  return apiFetch(`/admin/orders/history?${params.toString()}`)
}

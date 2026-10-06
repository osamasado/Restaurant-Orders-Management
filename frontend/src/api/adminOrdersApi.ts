import { apiFetch } from './http'
import type { AdminOrderPage, AdminOrderStatusResponse, OrderStatus } from './types'

/** Placed orders, newest first. Page 0 with a larger size is how the screen "loads more" and stays live. */
export function getAdminOrders(page: number, size: number): Promise<AdminOrderPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return apiFetch(`/admin/orders?${params.toString()}`)
}

/** Sends a step the row offered in nextStatuses - a 409 means another screen got there first. */
export function advanceAdminOrder(orderId: number, status: OrderStatus): Promise<AdminOrderStatusResponse> {
  return apiFetch(`/admin/orders/${orderId}/transition`, { method: 'POST', body: { status } })
}

export function cancelAdminOrder(orderId: number): Promise<AdminOrderStatusResponse> {
  return apiFetch(`/admin/orders/${orderId}/cancel`, { method: 'POST' })
}

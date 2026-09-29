import { apiFetch } from './http'
import type { KitchenOrderResponse, OrderStatus } from './types'

export function getKitchenOrders(): Promise<KitchenOrderResponse[]> {
  return apiFetch('/kitchen/orders')
}

/** Sends the status the card showed as next - a 409 means another screen got there first. */
export function advanceKitchenOrder(orderId: number, status: OrderStatus): Promise<KitchenOrderResponse> {
  return apiFetch(`/kitchen/orders/${orderId}/transition`, { method: 'POST', body: { status } })
}

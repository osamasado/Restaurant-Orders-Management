import { apiFetch } from './http'
import type { CancelledOrderResponse, KitchenMealResponse, KitchenOrderResponse, OrderStatus } from './types'

export function getKitchenOrders(): Promise<KitchenOrderResponse[]> {
  return apiFetch('/kitchen/orders')
}

/** Sends the status the card showed as next - a 409 means another screen got there first. */
export function advanceKitchenOrder(orderId: number, status: OrderStatus): Promise<KitchenOrderResponse> {
  return apiFetch(`/kitchen/orders/${orderId}/transition`, { method: 'POST', body: { status } })
}

export function getCancelledOrders(): Promise<CancelledOrderResponse[]> {
  return apiFetch('/kitchen/orders/cancelled')
}

/** Safe to repeat: the server keeps the first receipt, so two screens pressing at once is harmless. */
export function acknowledgeCancellation(orderId: number): Promise<void> {
  return apiFetch(`/kitchen/orders/${orderId}/acknowledge-cancellation`, { method: 'POST' })
}

export function getKitchenMeals(): Promise<KitchenMealResponse[]> {
  return apiFetch('/kitchen/meals')
}

/** Same effect as the admin's toggle: an unavailable meal leaves every guest menu and cannot be ordered. */
export function setMealAvailability(mealId: number, available: boolean): Promise<KitchenMealResponse> {
  return apiFetch(`/kitchen/meals/${mealId}/availability`, { method: 'PATCH', body: { available } })
}

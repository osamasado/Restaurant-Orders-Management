/**
 * sessionStorage, not localStorage: the placed order belongs to this tab's
 * sit-down, so a reload keeps the confirmation screen but a fresh tab (the
 * next guest) starts over. The order's secret (what lets this guest follow
 * this one order) is kept with its id, in the same place and for the same time.
 */
const ORDER_KEY = 'rom-guest-order'

export type StoredOrder = {
  orderId: number
  /** Null for an order stored before the secret existed (it is followed with the device code instead). */
  token: string | null
}

export function readStoredOrder(): StoredOrder | null {
  try {
    const stored = window.sessionStorage.getItem(ORDER_KEY)
    if (!stored) return null
    // An older version stored just the id, as a plain number.
    if (/^\d+$/.test(stored)) {
      const id = Number(stored)
      return id > 0 ? { orderId: id, token: null } : null
    }
    const parsed = JSON.parse(stored) as Partial<StoredOrder>
    const id = Number(parsed.orderId)
    if (!Number.isInteger(id) || id <= 0) return null
    return { orderId: id, token: typeof parsed.token === 'string' && parsed.token ? parsed.token : null }
  } catch {
    return null
  }
}

export function writeStoredOrder(order: StoredOrder) {
  try {
    window.sessionStorage.setItem(ORDER_KEY, JSON.stringify(order))
  } catch {
    // Storage unavailable (e.g. private browsing) - the confirmation just won't survive a reload.
  }
}

export function clearStoredOrder() {
  try {
    window.sessionStorage.removeItem(ORDER_KEY)
  } catch {
    // Nothing stored to clear.
  }
}

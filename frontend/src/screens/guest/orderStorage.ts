/**
 * sessionStorage, not localStorage: the placed order belongs to this tab's
 * sit-down, so a reload keeps the confirmation screen but a fresh tab (the
 * next guest) starts over.
 */
const ORDER_ID_KEY = 'rom-guest-order'

export function readStoredOrderId(): number | null {
  try {
    const stored = window.sessionStorage.getItem(ORDER_ID_KEY)
    const id = Number(stored)
    return Number.isInteger(id) && id > 0 ? id : null
  } catch {
    return null
  }
}

export function writeStoredOrderId(orderId: number) {
  try {
    window.sessionStorage.setItem(ORDER_ID_KEY, String(orderId))
  } catch {
    // Storage unavailable (e.g. private browsing) - the confirmation just won't survive a reload.
  }
}

export function clearStoredOrderId() {
  try {
    window.sessionStorage.removeItem(ORDER_ID_KEY)
  } catch {
    // Nothing stored to clear.
  }
}

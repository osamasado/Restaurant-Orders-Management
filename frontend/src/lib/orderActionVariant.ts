import type { OrderStatus } from '../api/types'

/**
 * The colour of the button that moves an order on to `status`: starting to prepare it is the main action (coral),
 * and every step after that (ready, served) is the order progressing (green). Cancelling is always the red button.
 */
export function advanceVariant(status: OrderStatus): 'button--primary' | 'button--success' {
  return status === 'PREPARING' ? 'button--primary' : 'button--success'
}

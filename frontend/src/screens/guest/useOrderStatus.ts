import { useEffect, useState } from 'react'
import { getOrderStatus } from '../../api/guestApi'
import { ApiError } from '../../api/http'
import type { GuestOrderStatusResponse, OrderStatus } from '../../api/types'

const POLL_MS = 5000

/** Once an order reaches one of these, it can't change any more - no point polling. */
const TERMINAL_STATUSES: OrderStatus[] = ['SERVED', 'CANCELLED']

type OrderStatusState = {
  /** Last successful response - kept while a later poll fails. */
  status: GuestOrderStatusResponse | null
  /** True while polls are failing (network down, server restarting). */
  connectionLost: boolean
}

/**
 * Polls the server for this order's live status (push updates are out of
 * scope this iteration). Stops at a terminal status; keeps the last good
 * response when a poll fails.
 */
export function useOrderStatus(orderId: number, deviceCode: string): OrderStatusState {
  const [state, setState] = useState<OrderStatusState>({ status: null, connectionLost: false })

  useEffect(() => {
    let cancelled = false
    let timer: number | undefined

    const poll = () => {
      getOrderStatus(orderId, deviceCode)
        .then((response) => {
          if (cancelled) return
          setState({ status: response, connectionLost: false })
          if (!TERMINAL_STATUSES.includes(response.status)) {
            timer = window.setTimeout(poll, POLL_MS)
          }
        })
        .catch((err: unknown) => {
          if (cancelled) return
          setState((previous) => ({ ...previous, connectionLost: true }))
          const permanent = err instanceof ApiError && (err.status === 403 || err.status === 404)
          if (!permanent) timer = window.setTimeout(poll, POLL_MS)
        })
    }

    poll()

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [orderId, deviceCode])

  return state
}

import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../../api/http'
import { getKitchenOrders } from '../../api/kitchenApi'
import type { KitchenOrderResponse } from '../../api/types'

const POLL_MS = 5000

type KitchenOrdersState = {
  /** Last successful response - kept while a later poll fails. */
  orders: KitchenOrderResponse[] | null
  /** True while polls are failing (network down, server restarting). */
  connectionLost: boolean
  /** The server no longer accepts this session (401) - polling stops. */
  sessionExpired: boolean
}

/**
 * Polls the kitchen board every 5 s - it never stops on its own, the board
 * is always live. refresh() re-polls immediately (after a button press or
 * a 409), which also restarts the 5 s cycle.
 */
export function useKitchenOrders() {
  const [state, setState] = useState<KitchenOrdersState>({
    orders: null,
    connectionLost: false,
    sessionExpired: false,
  })
  const [refreshCount, setRefreshCount] = useState(0)
  const refresh = useCallback(() => setRefreshCount((count) => count + 1), [])

  useEffect(() => {
    let cancelled = false
    let timer: number | undefined

    const poll = () => {
      getKitchenOrders()
        .then((orders) => {
          if (cancelled) return
          setState({ orders, connectionLost: false, sessionExpired: false })
          timer = window.setTimeout(poll, POLL_MS)
        })
        .catch((err: unknown) => {
          if (cancelled) return
          if (err instanceof ApiError && err.status === 401) {
            setState((previous) => ({ ...previous, sessionExpired: true }))
            return
          }
          setState((previous) => ({ ...previous, connectionLost: true }))
          timer = window.setTimeout(poll, POLL_MS)
        })
    }

    poll()

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [refreshCount])

  return { ...state, refresh }
}

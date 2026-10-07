import { useCallback, useEffect, useState } from 'react'
import { getAdminOrders } from '../../../../api/adminOrdersApi'
import type { AdminOrderPage } from '../../../../api/types'

const POLL_MS = 5000
export const ORDERS_STEP = 20
/** The server's page-size limit: the live list shows at most this many of the newest orders. */
export const ORDERS_LIMIT = 100

/**
 * Polls the admin Orders list every 5 s. "Load more" asks for a longer first page (the newest 20, 40, ...
 * up to the server's limit) instead of fetching further pages, so every poll returns one consistent list
 * and an order that moves does not show up twice or vanish between two pages. refresh() re-polls
 * immediately (after a button press or a 409), which also restarts the 5 s cycle. A failed poll keeps the
 * last list on screen and only raises connectionLost.
 */
export function useAdminOrders() {
  const [data, setData] = useState<AdminOrderPage | null>(null)
  const [size, setSize] = useState(ORDERS_STEP)
  const [connectionLost, setConnectionLost] = useState(false)
  const [refreshCount, setRefreshCount] = useState(0)
  const refresh = useCallback(() => setRefreshCount((count) => count + 1), [])
  const loadMore = useCallback(() => setSize((current) => Math.min(current + ORDERS_STEP, ORDERS_LIMIT)), [])

  useEffect(() => {
    let cancelled = false
    let timer: number | undefined

    const poll = () => {
      getAdminOrders(0, size)
        .then((result) => {
          if (cancelled) return
          setData(result)
          setConnectionLost(false)
        })
        .catch(() => {
          if (cancelled) return
          setConnectionLost(true)
        })
        .finally(() => {
          if (!cancelled) timer = window.setTimeout(poll, POLL_MS)
        })
    }

    poll()

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [size, refreshCount])

  const orders = data?.orders ?? null
  const totalOrders = data?.totalOrders ?? 0
  return {
    orders,
    totalOrders,
    connectionLost,
    refresh,
    loadMore,
    canLoadMore: orders !== null && orders.length < totalOrders && size < ORDERS_LIMIT,
    capped: orders !== null && totalOrders > ORDERS_LIMIT && size >= ORDERS_LIMIT,
  }
}

import { useCallback, useEffect, useState } from 'react'
import { getAdminOrders } from '../../../../api/adminOrdersApi'
import type { AdminOrderPage, OrderStatus } from '../../../../api/types'

const POLL_MS = 5000
export const ORDERS_STEP = 20
/** The server's page-size limit: the live list shows at most this many of the newest orders. */
export const ORDERS_LIMIT = 100

/** What the list is narrowed to: one status (null = all) and one day as yyyy-mm-dd ('' = any day). */
export type OrdersFilter = { status: OrderStatus | null; date: string }

/**
 * A day as the admin's own browser sees it: from local midnight (inclusive) to the next local midnight
 * (exclusive), as instants. The server needs no time zone: it just compares instants.
 */
export function dayRange(date: string): { from: string; to: string } {
  const [year, month, day] = date.split('-').map(Number)
  return {
    from: new Date(year, month - 1, day).toISOString(),
    to: new Date(year, month - 1, day + 1).toISOString(),
  }
}

/**
 * Polls the admin Orders list every 5 s. "Load more" asks for a longer first page (the newest 20, 40, ...
 * up to the server's limit) instead of fetching further pages, so every poll returns one consistent list
 * and an order that moves does not show up twice or vanish between two pages. refresh() re-polls
 * immediately (after a button press or a 409), which also restarts the 5 s cycle. A failed poll keeps the
 * last list on screen and only raises connectionLost.
 *
 * Changing the filter starts again from the newest 20, and a list fetched for another filter is never shown
 * (the screen says "loading" until the answer for the current filter arrives).
 */
export function useAdminOrders(filter: OrdersFilter) {
  const filterKey = `${filter.status ?? ''}|${filter.date}`
  const [data, setData] = useState<{ key: string; page: AdminOrderPage } | null>(null)
  const [sizeState, setSizeState] = useState({ key: filterKey, size: ORDERS_STEP })
  const size = sizeState.key === filterKey ? sizeState.size : ORDERS_STEP
  const [connectionLost, setConnectionLost] = useState(false)
  const [refreshCount, setRefreshCount] = useState(0)
  const refresh = useCallback(() => setRefreshCount((count) => count + 1), [])
  const loadMore = useCallback(
    () => setSizeState({ key: filterKey, size: Math.min(size + ORDERS_STEP, ORDERS_LIMIT) }),
    [filterKey, size],
  )

  const { status, date } = filter
  useEffect(() => {
    let cancelled = false
    let timer: number | undefined
    const key = `${status ?? ''}|${date}`
    const range = date ? dayRange(date) : {}

    const poll = () => {
      getAdminOrders(0, size, { status: status ?? undefined, ...range })
        .then((result) => {
          if (cancelled) return
          setData({ key, page: result })
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
  }, [size, refreshCount, status, date])

  const current = data?.key === filterKey ? data.page : null
  const orders = current?.orders ?? null
  const totalOrders = current?.totalOrders ?? 0
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

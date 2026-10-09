import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { getOrderHistory } from '../../../../api/orderHistoryApi'
import type { OrderHistory } from '../../../../api/types'
import { useLanguage } from '../../../../i18n/language-context'
import { useT } from '../../../../i18n/useT'
import { LOCALE_BY_LANGUAGE } from '../../../../lib/formatMoney'
import { OrderHistoryCard } from './OrderHistoryCard'
import './HistoryView.css'

const PAGE_SIZE = 20
const SEARCH_DELAY_MS = 300

/** The audit trail: every order, newest first, with each status change, its time and who made it. */
export function HistoryView() {
  const { t } = useT()
  const { language } = useLanguage()

  const [search, setSearch] = useState('')
  const [orders, setOrders] = useState<OrderHistory[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)

  // Only the newest request may change the screen: typing quickly must not let a slow, older answer overwrite a newer one.
  const latestRequest = useRef(0)

  const load = useCallback(async (pageToLoad: number, orderNumber: string, append: boolean) => {
    const request = ++latestRequest.current
    setLoading(true)
    try {
      const result = await getOrderHistory(pageToLoad, PAGE_SIZE, orderNumber ? Number(orderNumber) : undefined)
      if (request !== latestRequest.current) return
      setOrders((previous) => (append ? [...previous, ...result.orders] : result.orders))
      setPage(result.page)
      setTotalPages(result.totalPages)
      setLoadFailed(false)
    } catch {
      if (request !== latestRequest.current) return
      setLoadFailed(true)
    } finally {
      if (request === latestRequest.current) setLoading(false)
    }
  }, [])

  // First page on open, and again (from the top) whenever the search changes. The short delay while typing
  // avoids a request per keystroke; opening the screen does not wait.
  useEffect(() => {
    const timer = window.setTimeout(() => void load(0, search, false), search ? SEARCH_DELAY_MS : 0)
    return () => window.clearTimeout(timer)
  }, [search, load])

  const timeFormat = useMemo(
    () =>
      new Intl.DateTimeFormat(LOCALE_BY_LANGUAGE[language], {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hourCycle: 'h23',
      }),
    [language],
  )
  const formatTime = useCallback((isoTimestamp: string) => timeFormat.format(new Date(isoTimestamp)), [timeFormat])

  return (
    <div className="history-view">
      <div>
        <h2 className="history-view__title">{t('admin.nav.history')}</h2>
        <p className="history-view__subtitle">{t('admin.history.subtitle')}</p>
      </div>

      <label className="history-view__search">
        <span>{t('admin.history.searchLabel')}</span>
        <input
          inputMode="numeric"
          maxLength={9}
          placeholder="001"
          dir="ltr"
          value={search}
          onChange={(event) => setSearch(event.target.value.replace(/\D/g, ''))}
        />
      </label>

      {loadFailed && <p className="history-view__error">{t('admin.history.loadError')}</p>}

      {!loading && !loadFailed && orders.length === 0 && (
        <p className="history-view__empty">{search ? t('admin.history.noResults') : t('admin.history.empty')}</p>
      )}

      <div className="history-view__list">
        {orders.map((order) => (
          <OrderHistoryCard key={order.orderId} order={order} formatTime={formatTime} />
        ))}
      </div>

      {loading && orders.length === 0 && !loadFailed && <p className="history-view__empty">{t('admin.history.loading')}</p>}

      {page + 1 < totalPages && (
        <button className="history-view__more button" disabled={loading} onClick={() => void load(page + 1, search, true)}>
          {loading ? t('admin.history.loading') : t('admin.history.loadMore')}
        </button>
      )}
    </div>
  )
}

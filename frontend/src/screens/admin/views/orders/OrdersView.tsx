import { useCallback, useEffect, useMemo, useState } from 'react'
import { advanceAdminOrder, cancelAdminOrder } from '../../../../api/adminOrdersApi'
import { ApiError } from '../../../../api/http'
import { getSettings } from '../../../../api/settingsApi'
import type { AdminOrderRow, ConfigResponse, OrderStatus } from '../../../../api/types'
import { useLanguage } from '../../../../i18n/language-context'
import { useT } from '../../../../i18n/useT'
import { formatMoney, LOCALE_BY_LANGUAGE } from '../../../../lib/formatMoney'
import { OrderRow } from './OrderRow'
import { ORDERS_LIMIT, useAdminOrders } from './useAdminOrders'
import './OrdersView.css'

/**
 * The live orders: every placed order, newest first, refreshed every 5 s. Each row offers the steps the server
 * says are legal plus Cancel (after a confirmation); the buttons only send a step, the state machine decides.
 */
export function OrdersView() {
  const { t } = useT()
  const { language } = useLanguage()
  const { orders, connectionLost, refresh, loadMore, canLoadMore, capped } = useAdminOrders()

  const [busyOrderId, setBusyOrderId] = useState<number | null>(null)
  const [confirmingOrderId, setConfirmingOrderId] = useState<number | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [settings, setSettings] = useState<ConfigResponse | null>(null)

  // The currency comes from the settings; until they arrive (or if they cannot be read) totals show as plain numbers.
  useEffect(() => {
    let cancelled = false
    getSettings()
      .then((config) => {
        if (!cancelled) setSettings(config)
      })
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [])

  const locale = LOCALE_BY_LANGUAGE[language]
  const timeOnly = useMemo(
    () => new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }),
    [locale],
  )
  const dateAndTime = useMemo(
    () =>
      new Intl.DateTimeFormat(locale, {
        day: '2-digit',
        month: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hourCycle: 'h23',
      }),
    [locale],
  )
  // Today's orders show just the time; an older one also shows its date.
  const formatTime = useCallback(
    (isoTimestamp: string) => {
      const placed = new Date(isoTimestamp)
      return (placed.toDateString() === new Date().toDateString() ? timeOnly : dateAndTime).format(placed)
    },
    [timeOnly, dateAndTime],
  )
  const formatTotal = useCallback(
    (amount: number) =>
      settings
        ? formatMoney(amount, language, settings.currencySymbol, settings.symbolPosition)
        : new Intl.NumberFormat(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(amount),
    [settings, language, locale],
  )

  const run = (order: AdminOrderRow, action: () => Promise<unknown>) => {
    setBusyOrderId(order.orderId)
    setConfirmingOrderId(null)
    setNotice(null)
    action()
      .catch((err: unknown) => {
        setNotice(
          err instanceof ApiError && err.status === 409 ? t('admin.orders.actionConflict') : t('admin.orders.actionError'),
        )
      })
      .finally(() => {
        setBusyOrderId(null)
        refresh()
      })
  }

  const handleAdvance = (order: AdminOrderRow, status: OrderStatus) =>
    run(order, () => advanceAdminOrder(order.orderId, status))
  const handleConfirmCancel = (order: AdminOrderRow) => run(order, () => cancelAdminOrder(order.orderId))

  return (
    <div className="orders-view">
      <div>
        <h2 className="orders-view__title">{t('admin.nav.orders')}</h2>
        <p className="orders-view__subtitle">{t('admin.orders.subtitle')}</p>
      </div>

      {notice && <p className="orders-view__notice">{notice}</p>}
      {connectionLost && orders !== null && <p className="orders-view__notice">{t('admin.orders.connectionLost')}</p>}
      {connectionLost && orders === null && <p className="orders-view__error">{t('admin.orders.loadError')}</p>}

      {orders === null && !connectionLost && <p className="orders-view__empty">{t('admin.orders.loading')}</p>}
      {orders !== null && orders.length === 0 && <p className="orders-view__empty">{t('admin.orders.empty')}</p>}

      <div className="orders-view__list">
        {orders?.map((order) => (
          <OrderRow
            key={order.orderId}
            order={order}
            formatTime={formatTime}
            formatTotal={formatTotal}
            busy={busyOrderId === order.orderId}
            confirmingCancel={confirmingOrderId === order.orderId}
            onAdvance={handleAdvance}
            onAskCancel={(target) => setConfirmingOrderId(target.orderId)}
            onConfirmCancel={handleConfirmCancel}
            onKeep={() => setConfirmingOrderId(null)}
          />
        ))}
      </div>

      {canLoadMore && (
        <button type="button" className="orders-view__more" onClick={loadMore}>
          {t('admin.orders.loadMore')}
        </button>
      )}
      {capped && <p className="orders-view__empty">{t('admin.orders.capped', { count: ORDERS_LIMIT })}</p>}
    </div>
  )
}

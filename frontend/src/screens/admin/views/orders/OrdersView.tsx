import { useState } from 'react'
import type { OrderStatus } from '../../../../api/types'
import { useT } from '../../../../i18n/useT'
import { OrderRow } from './OrderRow'
import { ORDERS_LIMIT, useAdminOrders } from './useAdminOrders'
import type { OrdersFilter } from './useAdminOrders'
import { useOrderActions } from './useOrderActions'
import './OrdersView.css'

const FILTER_STATUSES: OrderStatus[] = ['SUBMITTED', 'PREPARING', 'READY', 'SERVED', 'CANCELLED']

/**
 * The live orders: every placed order, newest first, refreshed every 5 s. Each row offers the steps the server
 * says are legal plus Cancel (after a confirmation); the buttons only send a step, the state machine decides.
 */
export function OrdersView() {
  const { t } = useT()
  const [filter, setFilter] = useState<OrdersFilter>({ status: null, date: '' })
  const { orders, connectionLost, refresh, loadMore, canLoadMore, capped } = useAdminOrders(filter)
  const filterActive = filter.status !== null || filter.date !== ''

  const {
    busyOrderId,
    confirmingOrderId,
    notice,
    formatTime,
    formatTotal,
    handleAdvance,
    handleConfirmCancel,
    askCancel,
    keepOrder,
  } = useOrderActions(refresh)

  return (
    <div className="orders-view">
      <div>
        <h2 className="orders-view__title">{t('admin.nav.orders')}</h2>
        <p className="orders-view__subtitle">{t('admin.orders.subtitle')}</p>
      </div>

      <div className="orders-view__filters">
        <label className="orders-view__filter">
          <span>{t('admin.orders.statusLabel')}</span>
          <select
            value={filter.status ?? ''}
            onChange={(event) => setFilter({ ...filter, status: (event.target.value || null) as OrderStatus | null })}
          >
            <option value="">{t('admin.orders.statusAll')}</option>
            {FILTER_STATUSES.map((status) => (
              <option key={status} value={status}>
                {t(`admin.history.stages.${status}`)}
              </option>
            ))}
          </select>
        </label>
        <label className="orders-view__filter">
          <span>{t('admin.orders.dateLabel')}</span>
          <input
            type="date"
            dir="ltr"
            value={filter.date}
            onChange={(event) => setFilter({ ...filter, date: event.target.value })}
          />
        </label>
        {filterActive && (
          <button type="button" className="orders-view__clear button button--small" onClick={() => setFilter({ status: null, date: '' })}>
            {t('admin.orders.clearFilters')}
          </button>
        )}
      </div>

      {notice && <p className="orders-view__notice">{notice}</p>}
      {connectionLost && orders !== null && <p className="orders-view__notice">{t('admin.orders.connectionLost')}</p>}
      {connectionLost && orders === null && <p className="orders-view__error">{t('admin.orders.loadError')}</p>}

      {orders === null && !connectionLost && <p className="orders-view__empty">{t('admin.orders.loading')}</p>}
      {orders !== null && orders.length === 0 && (
        <p className="orders-view__empty">{filterActive ? t('admin.orders.noResults') : t('admin.orders.empty')}</p>
      )}

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
            onAskCancel={askCancel}
            onConfirmCancel={handleConfirmCancel}
            onKeep={keepOrder}
          />
        ))}
      </div>

      {canLoadMore && (
        <button type="button" className="orders-view__more button" onClick={loadMore}>
          {t('admin.orders.loadMore')}
        </button>
      )}
      {capped && <p className="orders-view__empty">{t('admin.orders.capped', { count: ORDERS_LIMIT })}</p>}
    </div>
  )
}

import { Fragment } from 'react'
import type { AdminOrderRow } from '../../api/types'
import { useT } from '../../i18n/useT'
import { isolate } from '../../lib/bidi'
import { formatOrderNumber } from '../../lib/formatOrderNumber'
import { StatusBadge } from './StatusBadge'
import './OrderReports.css'

type OrderReportsProps = {
  orders: AdminOrderRow[]
  selectedId: number | null
  onSelect: (order: AdminOrderRow) => void
  formatTime: (isoTimestamp: string) => string
  formatTotal: (amount: number) => string
}

/**
 * The recent orders as a table: number, table, what was ordered, amount, status and time. Choosing a row (the order
 * number is a real button, so the keyboard can) opens it in the order panel. Below 700 px the table turns into a
 * stack of small cards, each cell keeping its column name, instead of a squeezed table.
 */
export function OrderReports({ orders, selectedId, onSelect, formatTime, formatTotal }: OrderReportsProps) {
  const { t } = useT()

  return (
    <div className="order-reports">
      <table className="order-reports__table">
        <thead>
          <tr>
            <th scope="col">{t('admin.dashboard.columns.number')}</th>
            <th scope="col">{t('admin.dashboard.columns.table')}</th>
            <th scope="col">{t('admin.dashboard.columns.items')}</th>
            <th scope="col">{t('admin.dashboard.columns.amount')}</th>
            <th scope="col">{t('admin.dashboard.columns.status')}</th>
            <th scope="col">{t('admin.dashboard.columns.time')}</th>
          </tr>
        </thead>
        <tbody>
          {orders.map((order) => {
            const selected = order.orderId === selectedId
            const number = formatOrderNumber(order.orderNumber)
            return (
              <tr key={order.orderId} className={selected ? 'order-reports__row order-reports__row--selected' : 'order-reports__row'}>
                <td data-label={t('admin.dashboard.columns.number')}>
                  <button
                    type="button"
                    className="order-reports__select"
                    aria-pressed={selected}
                    aria-label={t('admin.dashboard.showOrder', { number: isolate(number) })}
                    onClick={() => onSelect(order)}
                  >
                    <span dir="ltr">{number}</span>
                  </button>
                </td>
                <td data-label={t('admin.dashboard.columns.table')}>
                  <bdi>{order.tableNumber}</bdi>
                </td>
                <td data-label={t('admin.dashboard.columns.items')} className="order-reports__items">
                  {order.items.map((item, index) => (
                    <Fragment key={index}>
                      {index > 0 && ', '}
                      <bdi>
                        {item.quantity}x {item.name}
                      </bdi>
                    </Fragment>
                  ))}
                </td>
                <td data-label={t('admin.dashboard.columns.amount')} className="order-reports__amount">
                  <bdi>{formatTotal(order.total)}</bdi>
                </td>
                <td data-label={t('admin.dashboard.columns.status')}>
                  <StatusBadge status={order.status} />
                </td>
                <td data-label={t('admin.dashboard.columns.time')} className="order-reports__time">
                  <time dateTime={order.placedAt}>
                    <bdi>{formatTime(order.placedAt)}</bdi>
                  </time>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

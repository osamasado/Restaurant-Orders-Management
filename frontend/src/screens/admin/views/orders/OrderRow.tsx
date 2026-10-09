import { Fragment } from 'react'
import type { AdminOrderRow, OrderStatus } from '../../../../api/types'
import { StatusBadge } from '../../../../components/admin/StatusBadge'
import { useT } from '../../../../i18n/useT'
import { isolate } from '../../../../lib/bidi'
import { formatOrderNumber } from '../../../../lib/formatOrderNumber'
import { advanceVariant } from '../../../../lib/orderActionVariant'
import './OrderRow.css'

type OrderRowProps = {
  order: AdminOrderRow
  /** Formats the placed time in the app's language - one formatter shared by every row. */
  formatTime: (isoTimestamp: string) => string
  formatTotal: (amount: number) => string
  /** An action on this order is in flight: its buttons wait. */
  busy: boolean
  /** The Cancel button was pressed and the row is asking "are you sure?". */
  confirmingCancel: boolean
  onAdvance: (order: AdminOrderRow, status: OrderStatus) => void
  onAskCancel: (order: AdminOrderRow) => void
  onConfirmCancel: (order: AdminOrderRow) => void
  onKeep: () => void
}

/**
 * One order: number, items, where and when, its status, and one button per legal next step plus Cancel. The
 * steps come from the server (nextStatuses), so a finished order shows "no further steps" and nothing here
 * decides what is legal.
 */
export function OrderRow({
  order,
  formatTime,
  formatTotal,
  busy,
  confirmingCancel,
  onAdvance,
  onAskCancel,
  onConfirmCancel,
  onKeep,
}: OrderRowProps) {
  const { t } = useT()
  const finished = order.nextStatuses.length === 0
  const notes = order.items.filter((item) => item.note)

  return (
    <article className="order-row" data-status={order.status}>
      <div className="order-row__main">
        <header className="order-row__header">
          <span className="order-row__number" dir="ltr">
            {formatOrderNumber(order.orderNumber)}
          </span>
          <StatusBadge status={order.status} />
        </header>

        <p className="order-row__items">
          {order.items.map((item, index) => (
            <Fragment key={index}>
              {index > 0 && ', '}
              <bdi>
                {item.quantity}x {item.name}
                {item.size ? ` (${item.size})` : ''}
              </bdi>
            </Fragment>
          ))}
        </p>

        {notes.length > 0 && (
          <p className="order-row__notes">
            {notes.map((item, index) => (
              <Fragment key={index}>
                {index > 0 && ' · '}
                <bdi>
                  {item.name}: {item.note}
                </bdi>
              </Fragment>
            ))}
          </p>
        )}

        <p className="order-row__meta">
          <span>{t('admin.orders.table', { number: isolate(order.tableNumber) })}</span>
          <span aria-hidden="true">·</span>
          <time dateTime={order.placedAt}>
            <bdi>{formatTime(order.placedAt)}</bdi>
          </time>
          <span aria-hidden="true">·</span>
          <span>{t(`admin.settings.paymentMethodNames.${order.paymentMethod}`)}</span>
          <span aria-hidden="true">·</span>
          <bdi>{formatTotal(order.total)}</bdi>
        </p>
      </div>

      <div className="order-row__actions">
        {confirmingCancel ? (
          <div className="order-row__confirm" role="group">
            <p className="order-row__confirm-text">
              {t('admin.orders.cancelConfirm', { number: isolate(formatOrderNumber(order.orderNumber)) })}
            </p>
            <div className="order-row__buttons">
              <button
                type="button"
                className="order-row__button order-row__button--cancel-yes button button--small button--danger"
                disabled={busy}
                onClick={() => onConfirmCancel(order)}
              >
                {t('admin.orders.cancelYes')}
              </button>
              <button type="button" className="order-row__button button button--small" disabled={busy} onClick={onKeep}>
                {t('admin.orders.cancelKeep')}
              </button>
            </div>
          </div>
        ) : finished ? (
          <span className="order-row__none">{t('admin.orders.noTransitions')}</span>
        ) : (
          <div className="order-row__buttons">
            {order.nextStatuses.map((status) => (
              <button
                key={status}
                type="button"
                className={`order-row__button order-row__button--advance button button--small ${advanceVariant(status)}`}
                disabled={busy}
                onClick={() => onAdvance(order, status)}
              >
                {t(`admin.orders.actions.${status}`)}
              </button>
            ))}
            <button
              type="button"
              className="order-row__button order-row__button--cancel button button--small button--danger"
              disabled={busy}
              onClick={() => onAskCancel(order)}
            >
              {t('admin.orders.cancel')}
            </button>
          </div>
        )}
      </div>
    </article>
  )
}

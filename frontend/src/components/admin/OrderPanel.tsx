import type { AdminOrderRow, OrderStatus } from '../../api/types'
import { useT } from '../../i18n/useT'
import { isolate } from '../../lib/bidi'
import { formatOrderNumber } from '../../lib/formatOrderNumber'
import { advanceVariant } from '../../lib/orderActionVariant'
import { EmptyState } from './EmptyState'
import { Icon } from '../Icon'
import { OrderItem } from './OrderItem'
import { StatusBadge } from './StatusBadge'
import './OrderPanel.css'

/** The steps an order moves through on the way to the table; cancelling is a side exit, not a step. */
const PROGRESS: OrderStatus[] = ['SUBMITTED', 'PREPARING', 'READY', 'SERVED']

type OrderPanelProps = {
  /** The order shown; null shows the "choose an order" hint. */
  order: AdminOrderRow | null
  formatTime: (isoTimestamp: string) => string
  formatTotal: (amount: number) => string
  busy: boolean
  confirmingCancel: boolean
  notice: string | null
  onAdvance: (order: AdminOrderRow, status: OrderStatus) => void
  onAskCancel: (order: AdminOrderRow) => void
  onConfirmCancel: (order: AdminOrderRow) => void
  onKeep: () => void
  /** True inside a dialog, which already shows the order number as its title. */
  headless?: boolean
  /** The photo of the meal an order line is for, found by the line's name; null when the menu has no such meal. */
  imageFor?: (mealName: string) => string | null
}

/**
 * The selected order, the way the kitchen and the guest see it: where and when, how far along it is, what was
 * ordered, what it came to, and the buttons for what may happen next. The next steps come from the server
 * (nextStatuses) and the total is the one the server computed, so nothing here decides what is legal or adds up
 * prices. Cancelling asks first, in place.
 */
export function OrderPanel({
  order,
  formatTime,
  formatTotal,
  busy,
  confirmingCancel,
  notice,
  onAdvance,
  onAskCancel,
  onConfirmCancel,
  onKeep,
  headless = false,
  imageFor,
}: OrderPanelProps) {
  const { t } = useT()

  if (order === null) {
    return (
      <aside className="order-panel order-panel--empty" aria-label={t('admin.dashboard.panel.title')}>
        <EmptyState icon="orders" title={t('admin.dashboard.panel.noneTitle')} hint={t('admin.dashboard.panel.noneHint')} />
      </aside>
    )
  }

  const number = formatOrderNumber(order.orderNumber)
  const finished = order.nextStatuses.length === 0
  const currentStep = PROGRESS.indexOf(order.status)

  return (
    <aside className="order-panel" aria-label={t('admin.dashboard.panel.title')}>
      <section className="order-panel__details">
        <h3 className="order-panel__eyebrow">{t('admin.dashboard.panel.details')}</h3>
        <p className="order-panel__detail">
          <Icon name="table" size={18} />
          <span>{t('admin.orders.table', { number: isolate(order.tableNumber) })}</span>
        </p>
        <p className="order-panel__detail">
          <Icon name="clock" size={18} />
          <time dateTime={order.placedAt}>
            <bdi>{formatTime(order.placedAt)}</bdi>
          </time>
        </p>
        <p className="order-panel__detail">
          <Icon name="payment" size={18} />
          <span>{t(`admin.settings.paymentMethodNames.${order.paymentMethod}`)}</span>
        </p>
      </section>

      <section className="order-panel__cart order-panel__cart--top">
        {!headless && (
          <header className="order-panel__header">
            <h3 className="order-panel__title">
              <Icon name="orders" size={20} />
              {t('admin.dashboard.panel.order')}
            </h3>
            <span className="order-panel__number" dir="ltr">
              {number}
            </span>
          </header>
        )}

        {order.status === 'CANCELLED' ? (
          <div className="order-panel__cancelled">
            <StatusBadge status="CANCELLED" />
          </div>
        ) : (
          <ol className="order-panel__progress" aria-label={t('admin.dashboard.panel.progress')}>
            {PROGRESS.map((step, index) => (
              <li key={step} aria-current={index === currentStep ? 'step' : undefined}>
                <StatusBadge status={step} progress={index < currentStep ? 'done' : index === currentStep ? 'current' : 'upcoming'} />
              </li>
            ))}
          </ol>
        )}

        <h4 className="order-panel__section-title">{t('admin.dashboard.panel.items')}</h4>
        <ul className="order-panel__items">
          {order.items.map((item, index) => (
            <OrderItem key={index} name={item.name} size={item.size} quantity={item.quantity} note={item.note} imageUrl={imageFor?.(item.name) ?? null} />
          ))}
        </ul>

      </section>

      <div className="order-panel__tear" aria-hidden="true" />

      <section className="order-panel__cart order-panel__cart--bottom">
        <p className="order-panel__total">
          <span>{t('admin.dashboard.panel.total')}</span>
          <strong>
            <bdi>{formatTotal(order.total)}</bdi>
          </strong>
        </p>

        {notice && (
          <p className="order-panel__notice" role="alert">
            {notice}
          </p>
        )}

        {confirmingCancel ? (
          <div className="order-panel__confirm" role="group" aria-label={t('admin.orders.cancel')}>
            <p>{t('admin.orders.cancelConfirm', { number: isolate(number) })}</p>
            <div className="order-panel__buttons">
              <button type="button" className="button button--large button--danger" disabled={busy} onClick={() => onConfirmCancel(order)}>
                {t('admin.orders.cancelYes')}
              </button>
              <button type="button" className="button button--large" disabled={busy} onClick={onKeep}>
                {t('admin.orders.cancelKeep')}
              </button>
            </div>
          </div>
        ) : finished ? (
          <p className="order-panel__none">{t('admin.orders.noTransitions')}</p>
        ) : (
          <div className="order-panel__buttons">
            {order.nextStatuses.map((status, index) => (
              <button
                key={status}
                type="button"
                className={`button button--large button--block ${advanceVariant(status)}`}
                disabled={busy && index > 0}
                aria-busy={busy && index === 0 ? true : undefined}
                onClick={() => {
                  if (!busy) onAdvance(order, status)
                }}
              >
                {t(`admin.orders.actions.${status}`)}
              </button>
            ))}
            <button type="button" className="button button--large button--block button--danger" disabled={busy} onClick={() => onAskCancel(order)}>
              {t('admin.orders.cancel')}
            </button>
          </div>
        )}
      </section>
    </aside>
  )
}

import type { KitchenOrderResponse, OrderStatus } from '../../api/types'
import { Icon } from '../../components/Icon'
import { useT } from '../../i18n/useT'
import { formatOrderNumber } from '../../lib/formatOrderNumber'
import { elapsedMs, timerLevel, formatElapsed } from '../../lib/orderTimer'
import { tableLabel } from '../../lib/tableLabel'
import './KitchenOrderCard.css'

/**
 * One button per kitchen step, keyed by the server's nextStatus - the card
 * never decides what's legal, it only draws what the server allows.
 */
const ACTIONS: Partial<Record<OrderStatus, { labelKey: string; variant: string }>> = {
  PREPARING: { labelKey: 'kitchen.actions.start', variant: 'start' },
  READY: { labelKey: 'kitchen.actions.ready', variant: 'ready' },
  SERVED: { labelKey: 'kitchen.actions.served', variant: 'served' },
}

type KitchenOrderCardProps = {
  order: KitchenOrderResponse
  /** Current time from the board's one shared tick, so all cards change together. */
  now: number
  /** True while this card's own request is in flight - blocks double taps. */
  busy: boolean
  onAdvance: (order: KitchenOrderResponse) => void
}

export function KitchenOrderCard({ order, now, busy, onAdvance }: KitchenOrderCardProps) {
  const { t } = useT()
  const action = order.nextStatus ? ACTIONS[order.nextStatus] : undefined
  const elapsed = elapsedMs(order.placedAt, now)
  const level = timerLevel(elapsed)

  return (
    <article className={`kitchen-order-card${order.takeAway ? ' kitchen-order-card--take-away' : ''}`}>
      <header className="kitchen-order-card__header">
        <span className={`kitchen-order-card__number kitchen-order-card__number--${order.status.toLowerCase()}`}>{formatOrderNumber(order.orderNumber)}</span>
        {/* The table, or "Take away" (this order is packed to go): a chip like the timer's, the take-away one solid coral with a bag. */}
        <span className={`kitchen-order-card__place${order.takeAway ? ' kitchen-order-card__place--take-away' : ''}`}>
          {order.takeAway && <Icon name="bag" size={15} />}
          {tableLabel(t, 'kitchen.table', order)}
        </span>
        <time
          className={`kitchen-order-card__timer kitchen-order-card__timer--${level}`}
          dateTime={order.placedAt}
        >
          {formatElapsed(elapsed)}
        </time>
      </header>

      <ul className="kitchen-order-card__items">
        {order.items.map((item, index) => (
          <li key={index} className="kitchen-order-card__item">
            <div className="kitchen-order-card__item-line">
              <span className="kitchen-order-card__quantity" dir="ltr">{item.quantity}×</span>
              <span className="kitchen-order-card__name"><bdi>{item.name}</bdi></span>
              <span className="kitchen-order-card__size"><bdi>{item.size}</bdi></span>
            </div>
            {item.note && (
              <p className="kitchen-order-card__note">
                <bdi>{item.note}</bdi>
              </p>
            )}
          </li>
        ))}
      </ul>

      {action && (
        <button
          type="button"
          className={`kitchen-order-card__action kitchen-order-card__action--${action.variant} button button--large ${action.variant === 'start' ? 'button--primary' : action.variant === 'ready' ? 'button--success' : ''}`}
          disabled={busy}
          onClick={() => onAdvance(order)}
        >
          {t(action.labelKey)}
        </button>
      )}
    </article>
  )
}

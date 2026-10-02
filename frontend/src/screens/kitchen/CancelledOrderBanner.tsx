import type { CancelledOrderResponse } from '../../api/types'
import { useT } from '../../i18n/useT'
import './CancelledOrderBanner.css'

type CancelledOrderBannerProps = {
  orders: CancelledOrderResponse[]
  /** The order whose acknowledge request is in flight - blocks double taps. */
  busyOrderId: number | null
  onAcknowledge: (order: CancelledOrderResponse) => void
}

/** Sits above the columns and stays until every cancellation is acknowledged - renders nothing when there are none. */
export function CancelledOrderBanner({ orders, busyOrderId, onAcknowledge }: CancelledOrderBannerProps) {
  const { t } = useT()

  if (orders.length === 0) {
    return null
  }

  return (
    <div className="cancelled-banner" role="alert">
      {orders.map((order) => (
        <div key={order.orderId} className="cancelled-banner__row">
          <span className="cancelled-banner__number">{order.orderNumber}</span>
          <span className="cancelled-banner__text">
            {t('kitchen.cancelled.banner', { table: order.tableNumber })}
          </span>
          <button
            type="button"
            className="cancelled-banner__action"
            disabled={busyOrderId === order.orderId}
            onClick={() => onAcknowledge(order)}
          >
            {t('kitchen.cancelled.acknowledge')}
          </button>
        </div>
      ))}
    </div>
  )
}

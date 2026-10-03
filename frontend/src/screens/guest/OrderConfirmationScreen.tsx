import type { ConfigResponse } from '../../api/types'
import { useLanguage } from '../../i18n/language-context'
import { useT } from '../../i18n/useT'
import { formatMoney } from '../../lib/formatMoney'
import { formatOrderNumber } from '../../lib/formatOrderNumber'
import { OrderStatusTimeline } from './OrderStatusTimeline'
import { useOrderStatus } from './useOrderStatus'
import './MealDetailScreen.css'
import './CartScreen.css'
import './OrderConfirmationScreen.css'

type OrderConfirmationScreenProps = {
  orderId: number
  deviceCode: string
  settings: ConfigResponse | null
  onNewOrder: () => void
}

/**
 * The guest's view after submitting: the order number, a live status
 * timeline (polled via useOrderStatus), and the server's totals. Everything
 * comes from the server, never from the cart preview.
 */
export function OrderConfirmationScreen({ orderId, deviceCode, settings, onNewOrder }: OrderConfirmationScreenProps) {
  const { t } = useT()
  const { language } = useLanguage()
  const { status, connectionLost } = useOrderStatus(orderId, deviceCode)

  const price = (amount: number) =>
    settings ? formatMoney(amount, language, settings.currencySymbol, settings.symbolPosition) : ''

  if (!status) {
    return (
      <div className="guest-screen guest-screen__checking">
        {connectionLost ? (
          <>
            <p>{t('guest.status.loadError')}</p>
            <button type="button" className="cart-screen__add-more" onClick={onNewOrder}>
              {t('guest.confirmation.newOrder')}
            </button>
          </>
        ) : (
          t('guest.status.loading')
        )}
      </div>
    )
  }

  return (
    <div className="guest-screen order-confirmation-screen">
      <section className="order-confirmation-screen__hero">
        <span className="order-confirmation-screen__eyebrow">{t('guest.confirmation.eyebrow')}</span>
        <span className="order-confirmation-screen__number" dir="ltr">
          {formatOrderNumber(status.orderNumber)}
        </span>
        <p className="order-confirmation-screen__guidance">{t('guest.confirmation.guidance')}</p>
      </section>

      <main className="guest-screen__content">
        <OrderStatusTimeline status={status} />
        {connectionLost && <p className="order-confirmation-screen__reconnecting">{t('guest.status.reconnecting')}</p>}
        <section className="cart-screen__totals">
          <div className="cart-screen__totals-row">
            <span>{t('guest.cart.subtotal')}</span>
            <span>{price(status.subtotal)}</span>
          </div>
          <div className="cart-screen__totals-row">
            <span>{t('guest.cart.vatAmount')}</span>
            <span>{price(status.taxAmount)}</span>
          </div>
          <div className="cart-screen__totals-row cart-screen__totals-row--total">
            <span>{t('guest.cart.total')}</span>
            <span>{price(status.total)}</span>
          </div>
          <p className="cart-screen__server-note">
            {t('guest.confirmation.paymentMethod', {
              method: t(`guest.payment.methods.${status.paymentMethod}.name`),
            })}
          </p>
        </section>
      </main>

      <button type="button" className="guest-screen__action-bar meal-detail-screen__cta" onClick={onNewOrder}>
        <span>{t('guest.confirmation.newOrder')}</span>
      </button>
    </div>
  )
}

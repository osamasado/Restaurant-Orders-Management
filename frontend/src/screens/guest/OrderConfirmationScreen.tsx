import type { ConfigResponse, GuestOrderResponse } from '../../api/types'
import { useLanguage } from '../../i18n/language-context'
import { useT } from '../../i18n/useT'
import { formatMoney } from '../../lib/formatMoney'
import './MealDetailScreen.css'
import './CartScreen.css'
import './OrderConfirmationScreen.css'

type OrderConfirmationScreenProps = {
  order: GuestOrderResponse
  settings: ConfigResponse | null
  onNewOrder: () => void
}

/**
 * Shows only what the submission returned - the authoritative number and
 * total, even if they differ from the cart preview. Hand-off point for #22,
 * which adds the live status timeline below the number.
 */
export function OrderConfirmationScreen({ order, settings, onNewOrder }: OrderConfirmationScreenProps) {
  const { t } = useT()
  const { language } = useLanguage()

  const price = (amount: number) =>
    settings ? formatMoney(amount, language, settings.currencySymbol, settings.symbolPosition) : ''

  return (
    <div className="guest-screen order-confirmation-screen">
      <section className="order-confirmation-screen__hero">
        <span className="order-confirmation-screen__eyebrow">{t('guest.confirmation.eyebrow')}</span>
        <span className="order-confirmation-screen__number" dir="ltr">
          {order.orderNumber}
        </span>
        <p className="order-confirmation-screen__guidance">{t('guest.confirmation.guidance')}</p>
      </section>

      <main className="guest-screen__content">
        <section className="cart-screen__totals">
          <div className="cart-screen__totals-row">
            <span>{t('guest.cart.subtotal')}</span>
            <span>{price(order.subtotal)}</span>
          </div>
          <div className="cart-screen__totals-row">
            <span>{t('guest.cart.vatAmount')}</span>
            <span>{price(order.taxAmount)}</span>
          </div>
          <div className="cart-screen__totals-row cart-screen__totals-row--total">
            <span>{t('guest.cart.total')}</span>
            <span>{price(order.total)}</span>
          </div>
          <p className="cart-screen__server-note">
            {t('guest.confirmation.paymentMethod', {
              method: t(`guest.payment.methods.${order.paymentMethod}.name`),
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

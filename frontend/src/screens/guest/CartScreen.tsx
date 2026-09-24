import type { CartQuoteResponse, ConfigResponse } from '../../api/types'
import { useLanguage } from '../../i18n/language-context'
import { useT } from '../../i18n/useT'
import { formatMoney } from '../../lib/formatMoney'
import { MAX_QUANTITY, MIN_QUANTITY, type CartLineItem } from './cartTypes'
import './MealDetailScreen.css'
import './CartScreen.css'

type CartScreenProps = {
  items: CartLineItem[]
  settings: ConfigResponse | null
  quote: CartQuoteResponse | null
  quoteLoading: boolean
  quoteError: boolean
  onBack: () => void
  onChangeQuantity: (lineId: string, quantity: number) => void
  onRemove: (lineId: string) => void
  onChoosePayment: () => void
  /** Set when a submit bounced back here, e.g. because a meal ran out meanwhile. */
  notice: string | null
}

/**
 * Line totals are shown from the local unit price for instant feedback; the
 * subtotal/tax/total panel only ever shows the server quote.
 */
export function CartScreen({
  items,
  settings,
  quote,
  quoteLoading,
  quoteError,
  onBack,
  onChangeQuantity,
  onRemove,
  onChoosePayment,
  notice,
}: CartScreenProps) {
  const { t } = useT()
  const { language } = useLanguage()

  const price = (amount: number) =>
    settings ? formatMoney(amount, language, settings.currencySymbol, settings.symbolPosition) : ''

  const unavailableSizeIds = new Set(quote?.lines.filter((line) => !line.available).map((line) => line.sizeId))
  // Only move on with a current, error-free quote and nothing unavailable - the server would reject it anyway.
  const canChoosePayment = Boolean(quote && !quoteLoading && !quoteError && unavailableSizeIds.size === 0)

  return (
    <div className="guest-screen cart-screen">
      <header className="guest-screen__header cart-screen__header">
        <button type="button" className="cart-screen__back" onClick={onBack} aria-label={t('guest.cart.back')}>
          <span aria-hidden="true">←</span>
        </button>
        <h1 className="cart-screen__title">{t('guest.cart.title')}</h1>
      </header>

      <main className="guest-screen__content">
        {notice && (
          <p className="cart-screen__notice" role="alert">
            {notice}
          </p>
        )}
        <ul className="cart-screen__lines">
          {items.map((item) => (
            <li className="cart-screen__line" key={item.id}>
              <div className="cart-screen__line-head">
                <div>
                  <div className="cart-screen__line-name">{item.name}</div>
                  <div className="cart-screen__line-meta">
                    {t('guest.cart.each', { size: item.size, price: price(item.unitPrice) })}
                  </div>
                </div>
                <span className="cart-screen__line-total">{price(item.unitPrice * item.quantity)}</span>
              </div>

              {item.note && <div className="cart-screen__line-note">{item.note}</div>}
              {unavailableSizeIds.has(item.sizeId) && (
                <div className="cart-screen__line-unavailable">{t('guest.cart.unavailable')}</div>
              )}

              <div className="cart-screen__line-actions">
                <div className="meal-detail-screen__stepper">
                  <button
                    type="button"
                    className="meal-detail-screen__stepper-button"
                    onClick={() => onChangeQuantity(item.id, item.quantity - 1)}
                    disabled={item.quantity <= MIN_QUANTITY}
                    aria-label={t('guest.cart.decreaseQuantity')}
                  >
                    −
                  </button>
                  <span className="meal-detail-screen__stepper-count">{item.quantity}</span>
                  <button
                    type="button"
                    className="meal-detail-screen__stepper-button"
                    onClick={() => onChangeQuantity(item.id, item.quantity + 1)}
                    disabled={item.quantity >= MAX_QUANTITY}
                    aria-label={t('guest.cart.increaseQuantity')}
                  >
                    +
                  </button>
                </div>
                <button type="button" className="cart-screen__remove" onClick={() => onRemove(item.id)}>
                  {t('guest.cart.remove')}
                </button>
              </div>
            </li>
          ))}
        </ul>

        <section className="cart-screen__totals" aria-live="polite">
          {quoteError ? (
            <p className="cart-screen__totals-status">{t('guest.cart.quoteError')}</p>
          ) : !quote ? (
            <p className="cart-screen__totals-status">{t('guest.cart.calculating')}</p>
          ) : (
            <>
              <div className="cart-screen__totals-row">
                <span>{t('guest.cart.subtotal')}</span>
                <span>{price(quote.subtotal)}</span>
              </div>
              <div className="cart-screen__totals-row">
                <span>{t('guest.cart.vat', { rate: quote.taxRate })}</span>
                <span>{price(quote.taxAmount)}</span>
              </div>
              <div className="cart-screen__totals-row cart-screen__totals-row--total">
                <span>{t('guest.cart.total')}</span>
                <span className={quoteLoading ? 'cart-screen__stale' : undefined}>{price(quote.total)}</span>
              </div>
            </>
          )}
          <p className="cart-screen__server-note">{t('guest.cart.serverNote')}</p>
        </section>

        <button type="button" className="cart-screen__add-more" onClick={onBack}>
          {t('guest.cart.addMore')}
        </button>
      </main>

      <button
        type="button"
        className="guest-screen__action-bar meal-detail-screen__cta"
        onClick={onChoosePayment}
        disabled={!canChoosePayment}
      >
        <span>{t('guest.cart.choosePayment')}</span>
        <span aria-hidden="true">→</span>
      </button>
    </div>
  )
}

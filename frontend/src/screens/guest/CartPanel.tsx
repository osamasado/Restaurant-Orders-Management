import type { CartQuoteResponse, ConfigResponse } from '../../api/types'
import { DirectionalArrow } from '../../components/DirectionalArrow'
import { Icon } from '../../components/Icon'
import { MealThumb } from '../../components/MealThumb'
import { EmptyState } from '../../components/admin/EmptyState'
import { useLanguage } from '../../i18n/language-context'
import { useT } from '../../i18n/useT'
import { isolate } from '../../lib/bidi'
import { formatMoney } from '../../lib/formatMoney'
import { MAX_QUANTITY, MIN_QUANTITY, type CartLineItem } from './cartTypes'
import './CartPanel.css'

type CartPanelProps = {
  items: CartLineItem[]
  settings: ConfigResponse | null
  quote: CartQuoteResponse | null
  quoteLoading: boolean
  quoteError: boolean
  onChangeQuantity: (lineId: string, quantity: number) => void
  onRemove: (lineId: string) => void
  onChoosePayment: () => void
  /** Set when a submit bounced back here, e.g. because a meal ran out meanwhile. */
  notice: string | null
  /** The cart screen already has a title bar; the panel beside the menu brings its own. */
  showTitle?: boolean
}

/**
 * The guest's cart, as one panel: the lines with their quantity controls, the totals and the button to pay. It is
 * the right-hand panel beside the menu on a wide screen and the body of the cart screen on a phone. Line totals
 * show from the local unit price for instant feedback; the subtotal, VAT and total only ever show the server's
 * quote, and the button waits for a current quote with nothing unavailable (the server would refuse it anyway).
 */
export function CartPanel({
  items,
  settings,
  quote,
  quoteLoading,
  quoteError,
  onChangeQuantity,
  onRemove,
  onChoosePayment,
  notice,
  showTitle = true,
}: CartPanelProps) {
  const { t } = useT()
  const { language } = useLanguage()

  const price = (amount: number) =>
    settings ? formatMoney(amount, language, settings.currencySymbol, settings.symbolPosition) : ''

  const unavailableSizeIds = new Set(quote?.lines.filter((line) => !line.available).map((line) => line.sizeId))
  const canChoosePayment = Boolean(quote && !quoteLoading && !quoteError && unavailableSizeIds.size === 0)

  if (items.length === 0) {
    return (
      <aside className="cart-panel cart-panel--empty" aria-label={t('guest.cart.title')}>
        <EmptyState icon="orders" title={t('guest.cart.empty')} hint={t('guest.cart.emptyHint')} />
      </aside>
    )
  }

  return (
    <aside className="cart-panel" aria-label={t('guest.cart.title')}>
      {showTitle && (
        <header className="cart-panel__header">
          <h2 className="cart-panel__title">
            <Icon name="orders" size={20} />
            {t('guest.cart.title')}
          </h2>
        </header>
      )}

      {notice && (
        <p className="cart-screen__notice" role="alert">
          {notice}
        </p>
      )}

      <ul className="cart-panel__lines">
        {items.map((item) => (
          <li className="cart-panel__line" key={item.id}>
            <div className="cart-panel__line-head">
              <MealThumb imageUrl={item.imageUrl} />
              <div className="cart-panel__line-text">
                <div className="cart-panel__line-name">
                  <bdi>{item.name}</bdi>
                </div>
                <div className="cart-panel__line-meta">
                  {t('guest.cart.each', { size: isolate(item.size), price: price(item.unitPrice) })}
                </div>
              </div>
              <span className="cart-panel__line-total">
                <bdi>{price(item.unitPrice * item.quantity)}</bdi>
              </span>
            </div>

            {item.note && <div className="cart-panel__line-note">{item.note}</div>}
            {unavailableSizeIds.has(item.sizeId) && (
              <div className="cart-panel__line-unavailable">{t('guest.cart.unavailable')}</div>
            )}

            <div className="cart-panel__line-actions">
              <div className="meal-detail-screen__stepper">
                <button
                  type="button"
                  className="stepper-button meal-detail-screen__stepper-button"
                  onClick={() => onChangeQuantity(item.id, item.quantity - 1)}
                  disabled={item.quantity <= MIN_QUANTITY}
                  aria-label={t('guest.cart.decreaseQuantity')}
                >
                  −
                </button>
                <span className="meal-detail-screen__stepper-count">{item.quantity}</span>
                <button
                  type="button"
                  className="stepper-button stepper-button--accent meal-detail-screen__stepper-button"
                  onClick={() => onChangeQuantity(item.id, item.quantity + 1)}
                  disabled={item.quantity >= MAX_QUANTITY}
                  aria-label={t('guest.cart.increaseQuantity')}
                >
                  +
                </button>
              </div>
              <button type="button" className="cart-panel__remove button button--small button--danger" onClick={() => onRemove(item.id)}>
                {t('guest.cart.remove')}
              </button>
            </div>
          </li>
        ))}
      </ul>

      <div className="cart-panel__tear" aria-hidden="true" />

      <section className="cart-screen__totals cart-panel__totals" aria-live="polite">
        {quoteError ? (
          <p className="cart-screen__totals-status">{t('guest.cart.quoteError')}</p>
        ) : !quote ? (
          <p className="cart-screen__totals-status">{t('guest.cart.calculating')}</p>
        ) : (
          <>
            <div className="cart-screen__totals-row">
              <span>{t('guest.cart.subtotal')}</span>
              <span>
                <bdi>{price(quote.subtotal)}</bdi>
              </span>
            </div>
            <div className="cart-screen__totals-row">
              <span>{t('guest.cart.vat', { rate: quote.taxRate })}</span>
              <span>
                <bdi>{price(quote.taxAmount)}</bdi>
              </span>
            </div>
            <div className="cart-screen__totals-row cart-screen__totals-row--total">
              <span>{t('guest.cart.total')}</span>
              <span className={quoteLoading ? 'cart-screen__stale' : undefined}>
                <bdi>{price(quote.total)}</bdi>
              </span>
            </div>
          </>
        )}
        <p className="cart-screen__server-note">{t('guest.cart.serverNote')}</p>
      </section>

      <button
        type="button"
        className="button button--primary button--large button--block cart-panel__cta"
        onClick={onChoosePayment}
        disabled={!canChoosePayment}
      >
        <span>{t('guest.cart.choosePayment')}</span>
        <DirectionalArrow direction="forward" />
      </button>
    </aside>
  )
}

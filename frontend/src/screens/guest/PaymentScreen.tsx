import { useState } from 'react'
import type { CartQuoteResponse, ConfigResponse, PaymentMethod } from '../../api/types'
import { useLanguage } from '../../i18n/language-context'
import { useT } from '../../i18n/useT'
import { formatMoney } from '../../lib/formatMoney'
import './MealDetailScreen.css'
import './CartScreen.css'
import './PaymentScreen.css'

/** Short mono tags from the design prototype (id.slice(0, 4).toUpperCase(), with CASH_DESK read as DESK). */
const METHOD_TAGS: Record<PaymentMethod, string> = {
  CASH: 'CASH',
  CARD: 'CARD',
  PAYPAL: 'PAYP',
  CASH_DESK: 'DESK',
}

/** Display order, independent of how the server happens to serialize the enabled set. */
const METHOD_ORDER: PaymentMethod[] = ['CASH', 'CARD', 'PAYPAL', 'CASH_DESK']

type PaymentScreenProps = {
  settings: ConfigResponse | null
  quote: CartQuoteResponse | null
  submitting: boolean
  error: string | null
  onBack: () => void
  onConfirm: (method: PaymentMethod) => void
}

/**
 * The total shown here is the server quote (a preview); the confirmation
 * screen shows whatever the submission itself returns.
 */
export function PaymentScreen({ settings, quote, submitting, error, onBack, onConfirm }: PaymentScreenProps) {
  const { t } = useT()
  const { language } = useLanguage()
  const [selected, setSelected] = useState<PaymentMethod | null>(null)

  const price = (amount: number) =>
    settings ? formatMoney(amount, language, settings.currencySymbol, settings.symbolPosition) : ''

  const methods = METHOD_ORDER.filter((method) => settings?.enabledPaymentMethods.includes(method))
  // A method disabled in Settings after it was picked is no longer valid.
  const selectedMethod = selected && methods.includes(selected) ? selected : null
  const canConfirm = Boolean(selectedMethod && quote && !submitting)

  return (
    <div className="guest-screen payment-screen">
      <header className="guest-screen__header cart-screen__header">
        <button
          type="button"
          className="cart-screen__back"
          onClick={onBack}
          disabled={submitting}
          aria-label={t('guest.payment.back')}
        >
          <span aria-hidden="true">←</span>
        </button>
        <h1 className="cart-screen__title">{t('guest.payment.title')}</h1>
      </header>

      <main className="guest-screen__content">
        <div className="payment-screen__methods" role="radiogroup" aria-label={t('guest.payment.title')}>
          {methods.map((method) => (
            <button
              type="button"
              role="radio"
              aria-checked={method === selectedMethod}
              key={method}
              className={
                'payment-screen__method' + (method === selectedMethod ? ' payment-screen__method--selected' : '')
              }
              onClick={() => setSelected(method)}
              disabled={submitting}
            >
              <span className="payment-screen__tag">{METHOD_TAGS[method]}</span>
              <span className="payment-screen__method-text">
                <span className="payment-screen__method-name">{t(`guest.payment.methods.${method}.name`)}</span>
                <span className="payment-screen__method-hint">{t(`guest.payment.methods.${method}.hint`)}</span>
              </span>
            </button>
          ))}
        </div>

        <section className="cart-screen__totals">
          <div className="cart-screen__totals-row cart-screen__totals-row--total">
            <span>{t('guest.payment.totalToPay')}</span>
            <span>{quote ? price(quote.total) : ''}</span>
          </div>
        </section>

        {error && (
          <p className="payment-screen__error" role="alert">
            {error}
          </p>
        )}
      </main>

      <button
        type="button"
        className="guest-screen__action-bar meal-detail-screen__cta"
        onClick={() => selectedMethod && onConfirm(selectedMethod)}
        disabled={!canConfirm}
      >
        {submitting ? (
          <span>{t('guest.payment.sending')}</span>
        ) : selectedMethod && quote ? (
          <>
            <span>{t('guest.payment.confirm')}</span>
            <span className="meal-detail-screen__cta-price">{price(quote.total)}</span>
          </>
        ) : (
          <span>{t('guest.payment.selectMethod')}</span>
        )}
      </button>
    </div>
  )
}

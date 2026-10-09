import type { CartQuoteResponse, ConfigResponse } from '../../api/types'
import { DirectionalArrow } from '../../components/DirectionalArrow'
import { useT } from '../../i18n/useT'
import type { CartLineItem } from './cartTypes'
import { CartPanel } from './CartPanel'
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

/** The cart as a screen of its own, for a phone; beside the menu on a wide screen the same panel sits next to it. */
export function CartScreen({ onBack, ...panel }: CartScreenProps) {
  const { t } = useT()

  return (
    <div className="guest-screen cart-screen">
      <header className="guest-screen__header cart-screen__header">
        <button type="button" className="cart-screen__back" onClick={onBack} aria-label={t('guest.cart.back')}>
          <DirectionalArrow direction="back" />
        </button>
        <h1 className="cart-screen__title">{t('guest.cart.title')}</h1>
      </header>

      <main className="guest-screen__content cart-screen__body">
        <CartPanel {...panel} showTitle={false} />
        <button type="button" className="button button--large button--block" onClick={onBack}>
          {t('guest.cart.addMore')}
        </button>
      </main>
    </div>
  )
}

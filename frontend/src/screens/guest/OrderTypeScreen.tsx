import { BrandMark } from '../../components/admin/BrandMark'
import { Icon } from '../../components/Icon'
import { useT } from '../../i18n/useT'
import { WelcomeBackdrop } from './WelcomeBackdrop'
import './OrderTypeScreen.css'

type OrderTypeScreenProps = {
  onEatIn: () => void
  onTakeAway: () => void
}

/**
 * The second screen, after the language: eat in the restaurant (which connects the device to a table, once) or take
 * the order away (which needs no table). Everything here follows the language just chosen. It sits on the same
 * canvas as the language screen so the two read as one start.
 */
export function OrderTypeScreen({ onEatIn, onTakeAway }: OrderTypeScreenProps) {
  const { t } = useT()

  return (
    <div className="welcome-screen order-type-screen">
      <WelcomeBackdrop />

      <main className="welcome-screen__content order-type-screen__content">
        <BrandMark size={60} />
        <h1 className="welcome-screen__title">{t('guest.orderType.title')}</h1>
        <p className="welcome-screen__tagline">{t('guest.orderType.hint')}</p>

        <div className="order-type-screen__choices">
          <button type="button" className="order-type-screen__choice button button--large" onClick={onEatIn}>
            <Icon name="utensils" size={30} />
            <span className="order-type-screen__choice-title">{t('guest.orderType.eatIn')}</span>
            <span className="order-type-screen__choice-hint">{t('guest.orderType.eatInHint')}</span>
          </button>
          <button type="button" className="order-type-screen__choice button button--large" onClick={onTakeAway}>
            <Icon name="bag" size={30} />
            <span className="order-type-screen__choice-title">{t('common.takeAway')}</span>
            <span className="order-type-screen__choice-hint">{t('guest.orderType.takeAwayHint')}</span>
          </button>
        </div>
      </main>
    </div>
  )
}

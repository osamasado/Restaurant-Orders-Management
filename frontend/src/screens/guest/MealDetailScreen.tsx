import { useState } from 'react'
import type { ConfigResponse, GuestMealResponse } from '../../api/types'
import { PhotoIcon } from '../../components/PhotoIcon'
import { useLanguage } from '../../i18n/language-context'
import { useT } from '../../i18n/useT'
import { formatMoney } from '../../lib/formatMoney'
import type { CartLineItem } from './cartTypes'
import './MealDetailScreen.css'

const MIN_QUANTITY = 1
const MAX_QUANTITY = 20

type MealDetailScreenProps = {
  meal: GuestMealResponse
  settings: ConfigResponse | null
  onBack: () => void
  onAddToOrder: (line: CartLineItem) => void
}

export function MealDetailScreen({ meal, settings, onBack, onAddToOrder }: MealDetailScreenProps) {
  const { t } = useT()
  const { language } = useLanguage()

  const [selectedSizeId, setSelectedSizeId] = useState<number | null>(meal.sizes[0]?.id ?? null)
  const [quantity, setQuantity] = useState(MIN_QUANTITY)
  const [note, setNote] = useState('')

  const selectedSize = meal.sizes.find((size) => size.id === selectedSizeId) ?? meal.sizes[0]
  const totalPrice = selectedSize ? selectedSize.price * quantity : 0
  const canAddToOrder = Boolean(selectedSize && settings)

  const price = (amount: number) =>
    settings ? formatMoney(amount, language, settings.currencySymbol, settings.symbolPosition) : ''

  const handleAddToOrder = () => {
    if (!selectedSize || !settings) return
    onAddToOrder({
      id: `${meal.id}-${selectedSize.id}`,
      mealId: meal.id,
      name: meal.name,
      size: selectedSize.label,
      quantity,
      unitPrice: selectedSize.price,
      note: note.trim() ? note.trim() : undefined,
    })
  }

  return (
    <div className="guest-screen meal-detail-screen">
      <div className="meal-detail-screen__hero">
        {meal.imageUrl ? (
          <img src={meal.imageUrl} alt="" className="meal-detail-screen__hero-photo" />
        ) : (
          <div className="meal-detail-screen__hero-placeholder" aria-hidden="true">
            <PhotoIcon />
          </div>
        )}
        <button
          type="button"
          className="meal-detail-screen__back"
          onClick={onBack}
          aria-label={t('guest.detail.back')}
        >
          <span aria-hidden="true">←</span>
        </button>
      </div>

      <div className="guest-screen__content meal-detail-screen__body">
        <h1 className="meal-detail-screen__title">{meal.name}</h1>
        {meal.description && <p className="meal-detail-screen__description">{meal.description}</p>}

        {meal.preparationMethod && (
          <div className="meal-detail-screen__prep">
            <span className="meal-detail-screen__label meal-detail-screen__label--forest">
              {t('guest.detail.preparation')}
            </span>
            <p className="meal-detail-screen__prep-text">{meal.preparationMethod}</p>
          </div>
        )}

        {meal.ingredients.length > 0 && (
          <>
            <span className="meal-detail-screen__label">{t('guest.detail.ingredients')}</span>
            <div className="meal-detail-screen__chips">
              {meal.ingredients.map((ingredient) => (
                <span className="meal-detail-screen__chip" key={ingredient}>
                  {ingredient}
                </span>
              ))}
            </div>
          </>
        )}

        {meal.sizes.length > 0 && (
          <>
            <span className="meal-detail-screen__label">{t('guest.detail.size')}</span>
            <div className="meal-detail-screen__sizes">
              {meal.sizes.map((size) => (
                <button
                  type="button"
                  key={size.id}
                  className={
                    'meal-detail-screen__size-row' +
                    (size.id === selectedSize?.id ? ' meal-detail-screen__size-row--selected' : '')
                  }
                  onClick={() => setSelectedSizeId(size.id)}
                >
                  <span>{size.label}</span>
                  <span>{price(size.price)}</span>
                </button>
              ))}
            </div>
          </>
        )}

        <span className="meal-detail-screen__label">{t('guest.detail.quantity')}</span>
        <div className="meal-detail-screen__stepper">
          <button
            type="button"
            className="meal-detail-screen__stepper-button"
            onClick={() => setQuantity((q) => Math.max(MIN_QUANTITY, q - 1))}
            disabled={quantity <= MIN_QUANTITY}
            aria-label={t('guest.detail.decreaseQuantity')}
          >
            −
          </button>
          <span className="meal-detail-screen__stepper-count">{quantity}</span>
          <button
            type="button"
            className="meal-detail-screen__stepper-button"
            onClick={() => setQuantity((q) => Math.min(MAX_QUANTITY, q + 1))}
            disabled={quantity >= MAX_QUANTITY}
            aria-label={t('guest.detail.increaseQuantity')}
          >
            +
          </button>
        </div>

        <span className="meal-detail-screen__label">{t('guest.detail.note')}</span>
        <textarea
          className="meal-detail-screen__note-input"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder={t('guest.detail.notePlaceholder')}
        />
      </div>

      <button
        type="button"
        className="guest-screen__action-bar meal-detail-screen__cta"
        onClick={handleAddToOrder}
        disabled={!canAddToOrder}
      >
        <span>{t('guest.detail.addToOrder')}</span>
        <span className="meal-detail-screen__cta-price">{price(totalPrice)}</span>
      </button>
    </div>
  )
}

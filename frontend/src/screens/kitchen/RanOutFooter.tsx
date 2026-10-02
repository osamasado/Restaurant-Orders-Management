import type { KitchenMealResponse } from '../../api/types'
import { useLanguage } from '../../i18n/language-context'
import { useT } from '../../i18n/useT'
import './RanOutFooter.css'

type RanOutFooterProps = {
  meals: KitchenMealResponse[]
  /** The meal whose toggle request is in flight - blocks double taps. */
  busyMealId: number | null
  onToggle: (meal: KitchenMealResponse) => void
}

/** The meal's name in the screen's language, falling back to English, then to whatever exists. */
function mealName(meal: KitchenMealResponse, language: string): string {
  const wanted = language.toUpperCase()
  const name =
    meal.names.find((entry) => entry.language === wanted) ?? meal.names.find((entry) => entry.language === 'EN') ?? meal.names[0]
  return name?.name ?? ''
}

/** One toggle chip per meal - a sold-out meal disappears from every guest menu at once. */
export function RanOutFooter({ meals, busyMealId, onToggle }: RanOutFooterProps) {
  const { t } = useT()
  const { language } = useLanguage()

  return (
    <footer className="kitchen-screen__footer">
      <div className="ran-out">
        <span className="ran-out__label">{t('kitchen.ranOut')}</span>
        <div className="ran-out__chips">
          {meals.map((meal) => (
            <button
              key={meal.id}
              type="button"
              className={`ran-out__chip${meal.available ? '' : ' ran-out__chip--sold-out'}`}
              aria-pressed={!meal.available}
              disabled={busyMealId === meal.id}
              onClick={() => onToggle(meal)}
            >
              {mealName(meal, language)} · {meal.available ? t('kitchen.availability.available') : t('kitchen.availability.soldOut')}
            </button>
          ))}
        </div>
      </div>
      <p className="ran-out__note">{t('kitchen.availability.note')}</p>
    </footer>
  )
}

import { useEffect, useState } from 'react'
import { getGuestMenu } from '../../api/guestApi'
import type { ConfigResponse, GuestCategoryResponse, GuestMealResponse, Language as GuestLanguage } from '../../api/types'
import { PhotoIcon } from '../../components/PhotoIcon'
import { useLanguage } from '../../i18n/language-context'
import { useT } from '../../i18n/useT'
import { formatMoney } from '../../lib/formatMoney'
import './MenuScreen.css'

function cheapestPrice(meal: GuestMealResponse): number {
  return Math.min(...meal.sizes.map((size) => size.price))
}

/** useLanguage() gives 'de'|'en'|'ar'; the guest API expects the backend's 'DE'|'EN'|'AR'. */
function toGuestLanguage(language: string): GuestLanguage {
  return language.toUpperCase() as GuestLanguage
}

type MenuScreenProps = {
  settings: ConfigResponse | null
  onSelectMeal: (meal: GuestMealResponse) => void
}

export function MenuScreen({ settings, onSelectMeal }: MenuScreenProps) {
  const { t } = useT()
  const { language } = useLanguage()

  const [categories, setCategories] = useState<GuestCategoryResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    getGuestMenu(toGuestLanguage(language))
      .then((menuResponse) => {
        if (cancelled) return
        setCategories(menuResponse)
      })
      .catch(() => {
        if (!cancelled) setError(t('guest.menu.loadError'))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- re-fetch is keyed on language only, t() itself shouldn't retrigger it
  }, [language])

  if (loading) {
    return <div className="menu-screen__status">{t('guest.menu.loading')}</div>
  }

  if (error) {
    return <div className="menu-screen__status menu-screen__status--error">{error}</div>
  }

  if (categories.length === 0) {
    return <div className="menu-screen__status">{t('guest.menu.empty')}</div>
  }

  return (
    <div className="menu-screen">
      {categories.map((category) => (
        <section key={category.id}>
          <span className="menu-category__label">{category.name}</span>
          <div className="menu-category__meals">
            {category.meals.map((meal) => (
              <button
                type="button"
                className="meal-card"
                key={meal.id}
                onClick={() => onSelectMeal(meal)}
              >
                {meal.imageUrl ? (
                  <img src={meal.imageUrl} alt="" className="meal-card__photo" />
                ) : (
                  <div className="meal-card__placeholder" aria-hidden="true">
                    <PhotoIcon />
                  </div>
                )}
                <div className="meal-card__info">
                  <span className="meal-card__name">{meal.name}</span>
                  {meal.description && <p className="meal-card__description">{meal.description}</p>}
                  {settings && (
                    <span className="meal-card__price">
                      {t('guest.menu.fromPrice', {
                        price: formatMoney(cheapestPrice(meal), language, settings.currencySymbol, settings.symbolPosition),
                      })}
                    </span>
                  )}
                </div>
              </button>
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}

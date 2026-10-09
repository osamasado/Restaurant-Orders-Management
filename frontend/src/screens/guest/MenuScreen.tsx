import { useEffect, useMemo, useState } from 'react'
import { getGuestMenu } from '../../api/guestApi'
import type { ConfigResponse, GuestCategoryResponse, GuestMealResponse, Language as GuestLanguage } from '../../api/types'
import { CategoryChips } from '../../components/admin/CategoryChips'
import type { CategoryChip } from '../../components/admin/CategoryChips'
import { EmptyState } from '../../components/admin/EmptyState'
import { SearchBar } from '../../components/admin/SearchBar'
import { Icon } from '../../components/Icon'
import { Slider } from '../../components/Slider'
import { useLanguage } from '../../i18n/language-context'
import { useT } from '../../i18n/useT'
import { formatMoney } from '../../lib/formatMoney'
import '../../components/admin/MealCard.css'
import './MenuScreen.css'

/** How often the open menu is refreshed, like the boards. */
const MENU_REFRESH_MS = 5000

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
  const [query, setQuery] = useState('')
  const [categoryId, setCategoryId] = useState<number | null>(null)

  // Loads the menu, then keeps it fresh: a meal the kitchen marks unavailable must vanish from a device that is
  // already showing the menu, not only from the next one that opens it. A refresh that fails keeps what is on
  // screen; only the very first load shows an error (and the refresh cycle then recovers it).
  useEffect(() => {
    let cancelled = false
    let timer: number | undefined

    const load = (first: boolean) => {
      getGuestMenu(toGuestLanguage(language))
        .then((menuResponse) => {
          if (cancelled) return
          setCategories(menuResponse)
          setError(null)
        })
        .catch(() => {
          if (!cancelled && first) setError(t('guest.menu.loadError'))
        })
        .finally(() => {
          if (cancelled) return
          if (first) setLoading(false)
          timer = window.setTimeout(() => load(false), MENU_REFRESH_MS)
        })
    }
    load(true)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- re-fetch is keyed on language only, t() itself shouldn't retrigger it
  }, [language])

  const chips: CategoryChip[] = useMemo(
    () => [
      { id: null, name: t('guest.menu.allCategories'), count: categories.reduce((n, c) => n + c.meals.length, 0), imageUrl: null },
      ...categories.map((category) => ({
        id: category.id,
        name: category.name,
        count: category.meals.length,
        imageUrl: category.meals.find((meal) => meal.imageUrl)?.imageUrl ?? null,
      })),
    ],
    [categories, t],
  )

  if (loading) {
    return <div className="menu-screen__status">{t('guest.menu.loading')}</div>
  }

  if (error) {
    return <div className="menu-screen__status menu-screen__status--error">{error}</div>
  }

  if (categories.length === 0) {
    return <div className="menu-screen__status">{t('guest.menu.empty')}</div>
  }

  const needle = query.trim().toLowerCase()
  const chosen = categories.filter((category) => categoryId === null || category.id === categoryId)
  const meals = chosen
    .flatMap((category) => category.meals)
    .filter((meal) => needle === '' || `${meal.name} ${meal.description ?? ''}`.toLowerCase().includes(needle))
  const rangeLabel = (from: number, to: number, total: number) => t('common.slider.range', { from, to, total })

  return (
    <div className="menu-screen">
      <SearchBar
        value={query}
        onChange={setQuery}
        label={t('guest.menu.searchLabel')}
        placeholder={t('guest.menu.searchPlaceholder')}
      />

      <CategoryChips
        heading={<h2 className="menu-screen__section-title">{t('guest.menu.categories')}</h2>}
        chips={chips}
        selectedId={categoryId}
        onSelect={setCategoryId}
        label={t('guest.menu.categories')}
        countLabel={(count) => t('guest.menu.mealCount', { count })}
        rangeLabel={rangeLabel}
      />

      {meals.length === 0 ? (
        <section aria-labelledby="menu-meals">
          <h2 className="menu-screen__section-title" id="menu-meals">
            {t('guest.menu.meals')}
          </h2>
          <EmptyState icon="search" title={t('guest.menu.noResults')} hint={t('guest.menu.noResultsHint')} />
        </section>
      ) : (
        <Slider
          heading={<h2 className="menu-screen__section-title">{t('guest.menu.meals')}</h2>}
          label={t('guest.menu.meals')}
          minItemWidth={230}
          gap={16}
          rangeLabel={rangeLabel}
        >
          {meals.map((meal) => (
            <article className="meal-card meal-tile" key={meal.id}>
              <div className="meal-tile__image">
                {meal.imageUrl ? (
                  <img src={meal.imageUrl} alt="" className="meal-card__photo" loading="lazy" />
                ) : (
                  <Icon name="utensils" size={32} />
                )}
              </div>
              <div className="meal-tile__body">
                <h3 className="meal-tile__name">
                  <button type="button" className="meal-card__open" onClick={() => onSelectMeal(meal)}>
                    {meal.name}
                  </button>
                </h3>
                {meal.description && <p className="meal-tile__description">{meal.description}</p>}
                <div className="meal-tile__footer meal-card__footer">
                  {settings && (
                    <span className="meal-tile__price">
                      {t('guest.menu.fromPrice', {
                        price: formatMoney(cheapestPrice(meal), language, settings.currencySymbol, settings.symbolPosition),
                      })}
                    </span>
                  )}
                  <span className="button button--primary button--small meal-card__add" aria-hidden="true">
                    <Icon name="plus" size={16} />
                    {t('guest.menu.add')}
                  </span>
                </div>
              </div>
            </article>
          ))}
        </Slider>
      )}
    </div>
  )
}

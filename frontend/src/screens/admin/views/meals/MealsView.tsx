import { useEffect, useState } from 'react'
import { deleteCategory, deleteMeal, listCategories, listMeals, setMealAvailability } from '../../../../api/menuApi'
import type { CategoryResponse, Language, MealResponse } from '../../../../api/types'
import { useT } from '../../../../i18n/useT'
import { CategoryFormModal } from './CategoryFormModal'
import { MealFormModal } from './MealFormModal'
import './MealsView.css'

type ModalState =
  | { type: 'category'; category: CategoryResponse | null }
  | { type: 'meal'; meal: MealResponse | null }
  | null

function toBackendLanguage(code: string): Language {
  const upper = code.toUpperCase()
  return upper === 'DE' || upper === 'AR' ? upper : 'EN'
}

function categoryName(category: CategoryResponse, language: Language): string {
  const match =
    category.translations.find((translation) => translation.language === language) ??
    category.translations.find((translation) => translation.language === 'EN') ??
    category.translations[0]
  return match?.name ?? `#${category.id}`
}

function mealName(meal: MealResponse, language: Language): string {
  const match =
    meal.translations.find((translation) => translation.language === language) ??
    meal.translations.find((translation) => translation.language === 'EN') ??
    meal.translations[0]
  return match?.name ?? `#${meal.id}`
}

function mealIngredientCount(meal: MealResponse, language: Language): number {
  const match =
    meal.translations.find((translation) => translation.language === language) ??
    meal.translations.find((translation) => translation.language === 'EN')
  return match?.ingredients.length ?? 0
}

function mealSizeLabels(meal: MealResponse, language: Language): string {
  return meal.sizes
    .map(
      (size) =>
        size.translations.find((translation) => translation.language === language)?.label ??
        size.translations.find((translation) => translation.language === 'EN')?.label,
    )
    .filter((label): label is string => Boolean(label))
    .join(' / ')
}

export function MealsView() {
  const { t, i18n } = useT()
  const language = toBackendLanguage(i18n.language)

  const [categories, setCategories] = useState<CategoryResponse[]>([])
  const [meals, setMeals] = useState<MealResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [modal, setModal] = useState<ModalState>(null)

  const refresh = async () => {
    try {
      const [categoriesResponse, mealsResponse] = await Promise.all([listCategories(), listMeals()])
      setCategories(categoriesResponse)
      setMeals(mealsResponse)
      setError(null)
    } catch {
      setError(t('admin.meals.loadError'))
    } finally {
      setLoading(false)
    }
  }

  // Fetch-on-mount: no data library (e.g. React Query) is in this project yet,
  // so this is the standard plain-effect data-fetching pattern from the React
  // docs; the hooks rules below don't have a clean alternative without one.
  // eslint-disable-next-line react-hooks/set-state-in-effect, react-hooks/exhaustive-deps
  useEffect(() => {
    void refresh()
  }, [])

  const closeModal = () => setModal(null)
  const handleSaved = () => {
    closeModal()
    void refresh()
  }

  const handleDeleteCategory = async (category: CategoryResponse) => {
    if (!window.confirm(t('admin.categories.confirmDelete'))) return
    try {
      await deleteCategory(category.id)
      void refresh()
    } catch {
      window.alert(t('admin.categories.deleteError'))
    }
  }

  const handleDeleteMeal = async (meal: MealResponse) => {
    if (!window.confirm(t('admin.meals.confirmDelete'))) return
    try {
      await deleteMeal(meal.id)
      void refresh()
    } catch {
      window.alert(t('admin.meals.deleteError'))
    }
  }

  const handleToggleAvailability = async (meal: MealResponse) => {
    try {
      const updated = await setMealAvailability(meal.id, !meal.available)
      setMeals((prev) => prev.map((existing) => (existing.id === updated.id ? updated : existing)))
    } catch {
      window.alert(t('admin.meals.availabilityError'))
    }
  }

  return (
    <div className="meals-view">
      <div>
        <h2 className="meals-view__title">{t('admin.nav.meals')}</h2>
        <p className="meals-view__subtitle">{t('admin.meals.subtitle')}</p>
      </div>

      {error && <p className="meals-view__error">{error}</p>}

      <section className="meals-view__section">
        <div className="meals-view__section-header">
          <span className="meals-view__section-title">{t('admin.categories.title')}</span>
          <button className="meals-view__add-button" onClick={() => setModal({ type: 'category', category: null })}>
            {t('admin.categories.add')}
          </button>
        </div>
        {categories.length === 0 && !loading && <p className="meals-view__empty">{t('admin.categories.empty')}</p>}
        {categories
          .slice()
          .sort((a, b) => a.sortOrder - b.sortOrder)
          .map((category) => (
            <div className="categories-row" key={category.id}>
              <span className="categories-row__name">{categoryName(category, language)}</span>
              <span className="categories-row__sort">{category.sortOrder}</span>
              <div className="categories-row__actions">
                <button onClick={() => setModal({ type: 'category', category })}>{t('admin.categories.edit')}</button>
                <button onClick={() => void handleDeleteCategory(category)}>{t('admin.categories.delete')}</button>
              </div>
            </div>
          ))}
      </section>

      <section className="meals-view__section">
        <div className="meals-view__section-header">
          <span className="meals-view__section-title">{t('admin.nav.meals')}</span>
          <button className="meals-view__add-button" onClick={() => setModal({ type: 'meal', meal: null })}>
            {t('admin.meals.add')}
          </button>
        </div>
        {meals.length === 0 && !loading && <p className="meals-view__empty">{t('admin.meals.empty')}</p>}
        {meals.map((meal) => {
          const category = categories.find((candidate) => candidate.id === meal.categoryId)

          return (
            <div className="meal-row" key={meal.id}>
              {meal.imageUrl ? (
                <img src={meal.imageUrl} alt="" className="meal-row__photo" />
              ) : (
                <div className="meal-row__placeholder" aria-hidden="true" />
              )}
              <div className="meal-row__info">
                <span className="meal-row__name">{mealName(meal, language)}</span>
                <span className="meal-row__meta">
                  {category ? categoryName(category, language) : ''} · {mealSizeLabels(meal, language)} ·{' '}
                  {t('admin.meals.ingredientsCount', { count: mealIngredientCount(meal, language) })}
                </span>
              </div>
              <button
                className={
                  meal.available
                    ? 'meal-row__availability meal-row__availability--available'
                    : 'meal-row__availability meal-row__availability--unavailable'
                }
                onClick={() => void handleToggleAvailability(meal)}
              >
                {meal.available ? t('admin.meals.available') : t('admin.meals.unavailable')}
              </button>
              <div className="meal-row__actions">
                <button onClick={() => setModal({ type: 'meal', meal })}>{t('admin.meals.editShort')}</button>
                <button onClick={() => void handleDeleteMeal(meal)}>{t('admin.meals.delete')}</button>
              </div>
            </div>
          )
        })}
      </section>

      {modal?.type === 'category' && (
        <CategoryFormModal category={modal.category} onClose={closeModal} onSaved={handleSaved} />
      )}
      {modal?.type === 'meal' && (
        <MealFormModal meal={modal.meal} categories={categories} onClose={closeModal} onSaved={handleSaved} />
      )}
    </div>
  )
}

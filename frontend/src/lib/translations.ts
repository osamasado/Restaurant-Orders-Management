import type { CategoryResponse, Language, MealResponse } from '../api/types'

/** The UI language code ('de') as the backend writes it ('DE'); anything unknown reads as English. */
export function toBackendLanguage(code: string): Language {
  const upper = code.toUpperCase()
  return upper === 'DE' || upper === 'AR' ? upper : 'EN'
}

/** The entry for the wanted language, else the English one, else the first: a menu item is never nameless. */
function pick<T extends { language: Language }>(translations: T[], language: Language): T | undefined {
  return (
    translations.find((translation) => translation.language === language) ??
    translations.find((translation) => translation.language === 'EN') ??
    translations[0]
  )
}

export function categoryName(category: CategoryResponse, language: Language): string {
  return pick(category.translations, language)?.name ?? `#${category.id}`
}

export function mealName(meal: MealResponse, language: Language): string {
  return pick(meal.translations, language)?.name ?? `#${meal.id}`
}

export function mealDescription(meal: MealResponse, language: Language): string | null {
  return pick(meal.translations, language)?.description ?? null
}

/** Every name a meal has in any language, for a search that finds "Schnitzel" whatever the screen language is. */
export function mealSearchText(meal: MealResponse): string {
  return meal.translations.map((translation) => translation.name).join(' ').toLowerCase()
}

/** The price a guest sees first: the cheapest size. */
export function lowestPrice(meal: MealResponse): number | null {
  return meal.sizes.length === 0 ? null : Math.min(...meal.sizes.map((size) => size.price))
}

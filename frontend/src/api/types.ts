export type Language = 'DE' | 'EN' | 'AR'

export type Role = 'ADMIN' | 'KITCHEN' | 'WAITER' | 'CASHIER' | 'USER'

export type StaffResponse = {
  id: number
  name: string
  role: Role
  lastSeenAt: string | null
}

export type CategoryTranslationDto = {
  language: Language
  name: string
}

export type CategoryRequest = {
  sortOrder: number
  translations: CategoryTranslationDto[]
}

export type CategoryResponse = {
  id: number
  sortOrder: number
  translations: CategoryTranslationDto[]
}

export type MealTranslationDto = {
  language: Language
  name: string
  description: string | null
  preparationMethod: string | null
  ingredients: string[]
}

export type MealSizeTranslationDto = {
  language: Language
  label: string
}

/** id is null for a new size being added on update - matches the backend's MealSizeDto. */
export type MealSizeDto = {
  id: number | null
  price: number
  translations: MealSizeTranslationDto[]
}

export type MealRequest = {
  categoryId: number
  available: boolean
  translations: MealTranslationDto[]
  sizes: MealSizeDto[]
}

export type MealResponse = {
  id: number
  categoryId: number
  available: boolean
  imageUrl: string | null
  translations: MealTranslationDto[]
  sizes: MealSizeDto[]
}

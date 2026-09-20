import { apiFetch } from './http'
import type { CategoryRequest, CategoryResponse, MealRequest, MealResponse } from './types'

export function listCategories(): Promise<CategoryResponse[]> {
  return apiFetch('/categories')
}

export function createCategory(request: CategoryRequest): Promise<CategoryResponse> {
  return apiFetch('/categories', { method: 'POST', body: request })
}

export function updateCategory(id: number, request: CategoryRequest): Promise<CategoryResponse> {
  return apiFetch(`/categories/${id}`, { method: 'PUT', body: request })
}

export function deleteCategory(id: number): Promise<void> {
  return apiFetch(`/categories/${id}`, { method: 'DELETE' })
}

export function listMeals(): Promise<MealResponse[]> {
  return apiFetch('/meals')
}

export function createMeal(request: MealRequest): Promise<MealResponse> {
  return apiFetch('/meals', { method: 'POST', body: request })
}

export function updateMeal(id: number, request: MealRequest): Promise<MealResponse> {
  return apiFetch(`/meals/${id}`, { method: 'PUT', body: request })
}

export function deleteMeal(id: number): Promise<void> {
  return apiFetch(`/meals/${id}`, { method: 'DELETE' })
}

export function setMealAvailability(id: number, available: boolean): Promise<MealResponse> {
  return apiFetch(`/meals/${id}/availability`, { method: 'PATCH', body: { available } })
}

export function uploadMealImage(id: number, file: File): Promise<MealResponse> {
  const formData = new FormData()
  formData.append('file', file)
  return apiFetch(`/meals/${id}/image`, { method: 'POST', body: formData })
}

export function deleteMealImage(id: number): Promise<MealResponse> {
  return apiFetch(`/meals/${id}/image`, { method: 'DELETE' })
}

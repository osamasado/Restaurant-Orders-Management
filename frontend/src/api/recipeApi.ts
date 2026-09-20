import { apiFetch } from './http'
import type { RecipeLineDto, RecipeLineRequest } from './types'

export function getRecipe(mealSizeId: number): Promise<RecipeLineDto[]> {
  return apiFetch(`/meal-sizes/${mealSizeId}/recipe`)
}

export function replaceRecipe(mealSizeId: number, lines: RecipeLineRequest[]): Promise<RecipeLineDto[]> {
  return apiFetch(`/meal-sizes/${mealSizeId}/recipe`, { method: 'PUT', body: lines })
}

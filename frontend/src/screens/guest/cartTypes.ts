/**
 * Frontend-only cart line shape - not a backend DTO, so it doesn't belong in
 * api/types.ts. `id` is `${mealId}-${sizeId}` rather than the size's label,
 * since the label is translated and would change with the guest's language.
 */
export type CartLineItem = {
  id: string
  mealId: number
  name: string
  size: string
  quantity: number
  unitPrice: number
  note: string | undefined
}

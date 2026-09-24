/**
 * Frontend-only cart line shape - not a backend DTO, so it doesn't belong in
 * api/types.ts. `id` is a per-line key (the same meal+size can appear twice
 * with different notes); `sizeId` is what the server prices the line by.
 * `unitPrice` is only for display - totals come from the server quote.
 */
export type CartLineItem = {
  id: string
  mealId: number
  sizeId: number
  name: string
  size: string
  quantity: number
  unitPrice: number
  note: string | undefined
}

/** Same bounds as the server's quote validation (GuestCartQuoteService). */
export const MIN_QUANTITY = 1
export const MAX_QUANTITY = 20

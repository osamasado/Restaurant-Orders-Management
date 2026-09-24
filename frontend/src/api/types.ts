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

export type RawMaterialRequest = {
  name: string
  unit: string
  inStockQuantity: number | null
  supplier: string | null
}

export type RawMaterialResponse = {
  id: number
  name: string
  unit: string
  inStockQuantity: number | null
  supplier: string | null
  imageUrl: string | null
}

export type RecipeLineRequest = {
  rawMaterialId: number
  quantity: number
}

export type RecipeLineDto = {
  rawMaterialId: number
  rawMaterialName: string
  unit: string
  quantity: number
}

export type DeviceStatus = 'UNPAIRED' | 'OFFLINE' | 'ONLINE'

export type TableRequest = {
  tableNumber: string
  room: string
  seats: number
}

export type TableResponse = {
  id: number
  tableNumber: string
  room: string
  seats: number
  pairedDeviceId: string | null
  deviceStatus: DeviceStatus
  lastSeenAt: string | null
}

export type StaffAccountCreateRequest = {
  name: string
  role: Role
  pin: string
}

export type StaffAccountUpdateRequest = {
  name: string
  role: Role
}

export type SymbolPosition = 'PREFIX' | 'SUFFIX'

export type PaymentMethod = 'CASH' | 'CARD' | 'PAYPAL' | 'CASH_DESK'

export type ConfigRequest = {
  currencyCode: string
  currencySymbol: string
  symbolPosition: SymbolPosition
  taxRate: number
  defaultLanguage: Language
  enabledPaymentMethods: PaymentMethod[]
}

export type ConfigResponse = {
  id: number
  currencyCode: string
  currencySymbol: string
  symbolPosition: SymbolPosition
  taxRate: number
  defaultLanguage: Language
  enabledPaymentMethods: PaymentMethod[]
}

export type GuestMealSizeResponse = {
  id: number
  price: number
  label: string
}

export type GuestMealResponse = {
  id: number
  categoryId: number
  name: string
  description: string | null
  preparationMethod: string | null
  ingredients: string[]
  imageUrl: string | null
  sizes: GuestMealSizeResponse[]
}

export type GuestCategoryResponse = {
  id: number
  name: string
  meals: GuestMealResponse[]
}

/** Guest cart preview - sizeId/quantity only, prices always come from the server. */
export type CartQuoteRequest = {
  items: { sizeId: number; quantity: number }[]
}

/** taxRate is a percentage (19 means 19%). */
export type CartQuoteResponse = {
  subtotal: number
  taxRate: number
  taxAmount: number
  total: number
  lines: { sizeId: number; unitPrice: number; lineTotal: number; available: boolean }[]
}

/** The table a paired guest device belongs to - resolved server-side from the device's pairing code. */
export type GuestTableResponse = {
  tableId: number
  tableNumber: string
  room: string
}

/** No prices or table id: the server resolves both itself. */
export type GuestOrderRequest = {
  deviceCode: string
  language: Language
  paymentMethod: PaymentMethod
  items: { sizeId: number; quantity: number; note: string | undefined }[]
}

/** The authoritative, server-computed result of a submission. */
export type GuestOrderResponse = {
  orderId: number
  orderNumber: number
  status: string
  placedAt: string
  paymentMethod: PaymentMethod
  subtotal: number
  taxAmount: number
  total: number
}

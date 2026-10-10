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

export type OrderStatus = 'DRAFT' | 'SUBMITTED' | 'PREPARING' | 'READY' | 'SERVED' | 'CANCELLED'

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

/** Where the displayed order number stands: the next one, how many orders are open (a restart is refused while there are any), the last restart. */
export type OrderNumberStatus = {
  nextDisplayNumber: number
  openOrders: number
  lastReset: { resetAt: string; staffName: string } | null
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
  status: OrderStatus
  placedAt: string
  paymentMethod: PaymentMethod
  subtotal: number
  taxAmount: number
  total: number
}

/** Live status of one of this device's orders - polled by the confirmation screen (#22). */
export type GuestOrderStatusResponse = {
  orderId: number
  orderNumber: number
  status: OrderStatus
  placedAt: string
  paymentMethod: PaymentMethod
  subtotal: number
  taxAmount: number
  total: number
  history: { status: OrderStatus; changedAt: string }[]
}

/** One card on the kitchen board. nextStatus is the one forward step the server allows, or null. */
export type KitchenOrderResponse = {
  orderId: number
  orderNumber: number
  tableNumber: string
  status: OrderStatus
  placedAt: string
  items: { name: string; size: string; quantity: number; note: string | null }[]
  nextStatus: OrderStatus | null
}

/**
 * One row of the admin Orders list. nextStatuses is computed by the server from the state machine
 * (every legal step except cancelling), so the screen never decides what is legal: empty once served or cancelled.
 */
export type AdminOrderRow = {
  orderId: number
  orderNumber: number
  tableNumber: string
  status: OrderStatus
  placedAt: string
  paymentMethod: PaymentMethod
  total: number
  items: { name: string; size: string; quantity: number; note: string | null }[]
  nextStatuses: OrderStatus[]
}

export type AdminOrderPage = {
  orders: AdminOrderRow[]
  page: number
  totalPages: number
  totalOrders: number
}

/** What an admin action answers with: the order's new status. */
export type AdminOrderStatusResponse = {
  orderId: number
  orderNumber: number
  status: OrderStatus
}

/** One line of the kitchen's cancelled-order banner - shown until a kitchen screen acknowledges it. */
export type CancelledOrderResponse = {
  orderId: number
  orderNumber: number
  tableNumber: string
}

/** One chip of the kitchen's "ran out?" footer - every translated name, so the screen picks its own language. */
export type KitchenMealResponse = {
  id: number
  available: boolean
  names: { language: Language; name: string }[]
}

/** One line of the hall board: the order's number and the table it is for - nothing else (no names, prices or items). */
export type HallBoardEntry = {
  orderNumber: number
  tableNumber: string
}

export type HallBoardResponse = {
  preparing: HallBoardEntry[]
  ready: HallBoardEntry[]
}

/** One status an order reached. actor is the staff member's name, or null for a guest submitting their own order. */
export type OrderHistoryEntry = {
  stage: OrderStatus
  changedAt: string
  actor: string | null
}

/** One order and its full audit trail, for the admin history screen. */
export type OrderHistory = {
  orderId: number
  orderNumber: number | null
  tableNumber: string
  status: OrderStatus
  placedAt: string | null
  entries: OrderHistoryEntry[]
  /** The kitchen's read-receipt for a cancelled order - not a status change; null until acknowledged. */
  cancellationAcknowledgement: { by: string | null; at: string } | null
}

export type OrderHistoryPage = {
  orders: OrderHistory[]
  page: number
  totalPages: number
  totalOrders: number
}

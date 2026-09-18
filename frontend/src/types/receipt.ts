export interface CheckoutLineItem {
  itemId: string
  quantity: number
}

export interface AdHocLineItem {
  name: string
  size: string | null
  flatPrice: number
  deposit: number
  quantity: number
}

export interface CheckoutPreviewRequest {
  startDatetime: string
  endDatetime: string
  items: CheckoutLineItem[]
  adHocItems: AdHocLineItem[]
  couponCode?: string | null
}

export interface CheckoutRequest {
  customerId: string
  startDatetime: string
  endDatetime: string
  items: CheckoutLineItem[]
  adHocItems: AdHocLineItem[]
  notes?: string
  couponCode?: string | null
}

export interface PreviewLineItem {
  itemId: string | null
  itemName: string
  rate: number
  deposit: number
  quantity: number
  rentalDays: number
  lineRent: number
  lineDeposit: number
  availableQuantity: number
}

export interface CheckoutPreview {
  allAvailable: boolean
  lineItems: PreviewLineItem[]
  rentalDays: number
  totalRent: number
  couponCode: string | null
  discountAmount: number
  totalDeposit: number
  grandTotal: number
  unavailableItems: string[]
}

export interface ReceiptLineItem {
  id: string
  itemId: string
  itemName: string
  thumbnailUrl: string | null
  itemSize: string | null
  itemCategory: string | null
  itemDescription: string | null
  quantity: number
  rateSnapshot: number
  depositSnapshot: number
  lineRent: number
  lineDeposit: number
  itemPurchaseRate: number | null  // null = no purchase cost recorded; disables damage-by-percentage
}

export interface Receipt {
  id: string
  receiptNumber: string
  shareToken: string
  customerId: string
  customerName: string
  customerPhone: string
  startDatetime: string
  endDatetime: string
  rentalDays: number
  totalRent: number
  couponCode: string | null
  discountAmount: number
  totalDeposit: number
  grandTotal: number
  status: 'GIVEN' | 'RETURNED'
  notes: string | null
  lineItems: ReceiptLineItem[]
  createdAt: string
}

export interface ReceiptSummary {
  id: string
  receiptNumber: string
  customerName: string
  customerPhone: string
  itemNames: string[]
  startDatetime: string
  endDatetime: string
  rentalDays: number
  totalRent: number
  couponCode: string | null
  discountAmount: number
  totalDeposit: number
  grandTotal: number
  status: 'GIVEN' | 'RETURNED'
  isOverdue: boolean
  overdueHours: number | null
}

interface CartItemBase {
  lineKey: string          // stable cart key and React key
  itemName: string
  size: string | null
  quantity: number
  deposit: number          // per unit
}

export interface CatalogueCartItem extends CartItemBase {
  kind: 'CATALOGUE'
  itemId: string
  itemType: 'INDIVIDUAL' | 'PACKAGE'
  category: string
  componentNames: string[] | null   // null for INDIVIDUAL; ["Name ×qty", ...] for PACKAGE
  thumbnailUrl: string | null
  rate: number             // per day
  availableQuantity: number
}

export interface AdHocCartItem extends CartItemBase {
  kind: 'ADHOC'
  flatPrice: number        // per unit, for the WHOLE rental — not per day
}

export type CartItem = CatalogueCartItem | AdHocCartItem

// The full preview response for an applied coupon, not just the discount amount — both the
// preview screen and the customer-confirmation screen render totalRent/totalDeposit/grandTotal
// from this single stored result rather than recomputing locally, so the two screens can never
// show different numbers for the same cart while a coupon is applied.
export interface AppliedCouponPreview {
  couponCode: string
  discountAmount: number
  totalRent: number
  totalDeposit: number
  grandTotal: number
}

export interface Cart {
  startDatetime: string   // ISO 8601 with IST offset
  endDatetime: string     // ISO 8601 with IST offset
  rentalDays: number
  items: CartItem[]
  appliedCoupon?: AppliedCouponPreview | null
}

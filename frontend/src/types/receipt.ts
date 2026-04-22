export interface CheckoutLineItem {
  itemId: string
  quantity: number
}

export interface CheckoutRequest {
  customerId: string
  startDatetime: string
  endDatetime: string
  items: CheckoutLineItem[]
  notes?: string
}

export interface PreviewLineItem {
  itemId: string
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
  totalDeposit: number
  grandTotal: number
  unavailableItems: string[]
}

export interface ReceiptLineItem {
  id: string
  itemId: string
  itemName: string
  quantity: number
  rateSnapshot: number
  depositSnapshot: number
  lineRent: number
  lineDeposit: number
}

export interface Receipt {
  id: string
  receiptNumber: string
  customerId: string
  customerName: string
  customerPhone: string
  startDatetime: string
  endDatetime: string
  rentalDays: number
  totalRent: number
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
  totalDeposit: number
  grandTotal: number
  status: 'GIVEN' | 'RETURNED'
  isOverdue: boolean
  overdueHours: number | null
}

export type PaymentMethod = 'CASH' | 'UPI' | 'OTHER'
export type TransactionType = 'COLLECT' | 'REFUND'

export interface ReturnLineItemRequest {
  receiptLineItemId: string
  isDamaged: boolean
  damagePercentage?: number
  adHocDamageAmount?: number
}

export interface ProcessReturnRequest {
  returnDatetime: string
  lineItems: ReturnLineItemRequest[]
  paymentMethod?: PaymentMethod
  damageNotes?: string
  notes?: string
}

export interface ReturnPreviewLineItem {
  receiptLineItemId: string
  itemName: string
  quantity: number
  lateFee: number
  damageCost: number
}

export interface ReturnPreview {
  totalLateFee: number
  totalDamageCost: number
  totalDeductions: number
  depositToReturn: number
  finalAmount: number
  transactionType: TransactionType
  lineItems: ReturnPreviewLineItem[]
}

export interface InvoiceLineItem {
  id: string
  itemId: string
  itemName: string
  itemSize: string | null
  itemCategory: string | null
  quantityReturned: number
  rateSnapshot: number
  depositSnapshot: number
  isDamaged: boolean
  damagePercentage: number | null
  damageCost: number
  lateFee: number
}

export interface Invoice {
  id: string
  invoiceNumber: string
  receiptId: string
  receiptNumber: string
  customerId: string
  customerName: string
  customerPhone: string
  returnDatetime: string
  totalRent: number
  totalDepositCollected: number
  totalLateFee: number
  totalDamageCost: number
  depositToReturn: number
  finalAmount: number
  transactionType: TransactionType
  paymentMethod: PaymentMethod
  damageNotes: string | null
  notes: string | null
  lineItems: InvoiceLineItem[]
  createdAt: string
}

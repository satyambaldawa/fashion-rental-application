export type CustomerType = 'STUDENT' | 'PROFESSIONAL' | 'MISC'

export interface Customer {
  id: string
  name: string
  phone: string
  address: string | null
  customerType: CustomerType
  organizationName: string | null
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface CustomerSummary {
  id: string
  name: string
  phone: string
  address: string | null
  customerType: CustomerType
  organizationName: string | null
  activeRentalsCount: number
}

export interface CreateCustomerRequest {
  name: string
  phone: string
  address?: string
  customerType: CustomerType
  organizationName?: string
}

export interface UpdateCustomerRequest {
  name: string
  phone: string
  address?: string
  customerType: CustomerType
  organizationName?: string
}

export interface CustomerReceiptItem {
  itemName: string
  quantity: number
}

export interface CustomerReceiptInvoice {
  id: string
  invoiceNumber: string
  finalAmount: number
  transactionType: 'COLLECT' | 'REFUND'
}

export interface CustomerReceipt {
  id: string
  receiptNumber: string
  status: 'GIVEN' | 'RETURNED'
  startDatetime: string
  endDatetime: string
  totalRent: number
  totalDeposit: number
  grandTotal: number
  items: CustomerReceiptItem[]
  invoice: CustomerReceiptInvoice | null
}

export interface CustomerDetail extends Customer {
  outstandingDeposit: number
  receipts: CustomerReceipt[]
}

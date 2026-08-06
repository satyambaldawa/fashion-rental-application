import { http, HttpResponse } from 'msw'
import * as f from './factories'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })
const page = (content: unknown[]) => ({
  content, totalElements: content.length, totalPages: 1, number: 0, size: 20,
})

/** Default happy-path handlers. Individual tests override with `server.use(...)`. */
export const handlers = [
  // auth
  http.post('*/api/auth/login', () => ok(f.aLoginResponse())),
  http.get('*/api/auth/users', () => ok([f.aUserRecord()])),
  http.post('*/api/auth/users', () => ok(f.aUserRecord())),
  http.put('*/api/auth/users/:id', () => ok(f.aUserRecord())),
  http.delete('*/api/auth/users/:id', () => ok(null)),

  // items
  http.get('*/api/items/:id/availability', () =>
    ok({ itemId: 'item-1', availableQuantity: 3, isAvailable: true })),
  http.get('*/api/items/:id', () => ok(f.anItemDetail())),
  http.get('*/api/items', () => ok(page([f.anItemSummary()]))),
  http.post('*/api/items/:id/clone', () => ok(f.anItemDetail())),
  http.post('*/api/items/:id/photos', () =>
    ok({ id: 'photo-1', url: 'u', thumbnailUrl: 't', sortOrder: 0 })),
  http.post('*/api/items', () => ok(f.anItemDetail())),
  http.put('*/api/items/:id', () => ok(f.anItemDetail())),
  http.patch('*/api/items/:id/photos/order', () => ok(null)),
  http.delete('*/api/items/:itemId/photos/:photoId', () => ok(null)),
  http.delete('*/api/items/:id', () => ok(null)),

  // customers
  http.get('*/api/customers/:id', () => ok(f.aCustomer())),
  http.get('*/api/customers', () => ok([f.aCustomerSummary()])),
  http.post('*/api/customers', () => ok(f.aCustomer())),
  http.put('*/api/customers/:id', () => ok(f.aCustomer())),

  // receipts / checkout
  http.post('*/api/checkout/preview', () => ok(f.aCheckoutPreview())),
  http.get('*/api/receipts/:id', () => ok(f.aReceipt())),
  http.get('*/api/receipts', () => ok([f.aReceiptSummary()])),
  http.post('*/api/receipts/:receiptId/return/preview', () => ok(f.aReturnPreview())),
  http.post('*/api/receipts/:receiptId/return', () => ok(f.anInvoice())),
  http.post('*/api/receipts', () => ok(f.aReceipt())),

  // invoices
  http.get('*/api/invoices/:id', () => ok(f.anInvoice())),

  // reports
  http.get('*/api/reports/daily-revenue', () => ok(f.aDailyRevenue())),
  http.get('*/api/reports/outstanding-deposits', () => ok(f.outstandingDeposits())),
  http.get('*/api/reports/overdue-rentals', () => ok(f.overdueRentals())),
  http.get('*/api/reports/monthly-revenue', () => ok(f.aMonthlyRevenue())),

  // config
  http.get('*/api/config/late-fee-rules', () => ok([f.aLateFeeRule()])),
  http.put('*/api/config/late-fee-rules', () => ok([f.aLateFeeRule()])),

  // public share pages
  http.get('*/api/public/receipts/:token', () => ok(f.aReceipt())),
  http.get('*/api/public/invoices/:token', () => ok(f.anInvoice())),
]

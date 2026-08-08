// Object-creation factories for frontend tests. Each returns a valid default that a
// test can partially override — so tests state only the fields they care about.
import type { CustomerSummary, Customer, CustomerDetail, CustomerReceipt } from '../types/customer'
import type { ItemSummary, ItemDetail } from '../types/inventory'
import type { Receipt, ReceiptSummary, CheckoutPreview } from '../types/receipt'
import type { Invoice, ReturnPreview } from '../types/invoice'
import type { DailyRevenue, OutstandingDeposits, OverdueRentals, MonthlyRevenue } from '../types/reports'
import type { LateFeeRule } from '../types/config'
import type { UserRecord, LoginResponse } from '../types/auth'

type Partialize<T> = (overrides?: Partial<T>) => T

export const anItemSummary: Partialize<ItemSummary> = (o = {}) => ({
  id: 'item-1', name: 'Royal Sherwani', category: 'COSTUME', itemType: 'INDIVIDUAL',
  size: 'M', description: 'Elegant sherwani', rate: 300, deposit: 1000,
  totalQuantity: 3, availableQuantity: 3, isAvailable: true,
  thumbnailUrl: null, photoUrls: [], componentNames: null, ...o,
})

export const anItemDetail: Partialize<ItemDetail> = (o = {}) => ({
  ...anItemSummary(), quantity: 3, isActive: true, notes: null,
  purchaseRate: 1500, vendorName: 'Vendor A', components: null, photos: [],
  createdAt: '2026-04-10T10:00:00+05:30', updatedAt: '2026-04-10T10:00:00+05:30', ...o,
})

export const aCustomer: Partialize<Customer> = (o = {}) => ({
  id: 'cust-1', name: 'Meera', phone: '9811122233', address: 'Pune',
  customerType: 'MISC', organizationName: null, isActive: true,
  createdAt: '2026-04-10T10:00:00+05:30', updatedAt: '2026-04-10T10:00:00+05:30', ...o,
})

export const aCustomerSummary: Partialize<CustomerSummary> = (o = {}) => ({
  id: 'cust-1', name: 'Meera', phone: '9811122233', address: 'Pune',
  customerType: 'MISC', organizationName: null, activeRentalsCount: 0, ...o,
})

export const aCustomerReceipt: Partialize<CustomerReceipt> = (o = {}) => ({
  id: 'rcpt-1', receiptNumber: 'R-20260418-003', status: 'GIVEN',
  startDatetime: '2026-04-18T10:00:00+05:30', endDatetime: '2026-04-20T10:00:00+05:30',
  totalRent: 400, totalDeposit: 1500, grandTotal: 1900,
  items: [{ itemName: 'Blue Sherwani', quantity: 2 }],
  invoice: null, ...o,
})

export const aCustomerDetail: Partialize<CustomerDetail> = (o = {}) => ({
  ...aCustomer(), outstandingDeposit: 0, receipts: [], ...o,
})

export const aReceipt: Partialize<Receipt> = (o = {}) => ({
  id: 'rcpt-1', receiptNumber: 'R-2026-0001', shareToken: 'share-r', customerId: 'cust-1',
  customerName: 'Meera', customerPhone: '9811122233',
  startDatetime: '2026-04-18T10:00:00+05:30', endDatetime: '2026-04-19T10:00:00+05:30',
  rentalDays: 1, totalRent: 300, totalDeposit: 1000, grandTotal: 1300, status: 'GIVEN',
  notes: null, createdAt: '2026-04-18T10:00:00+05:30',
  lineItems: [{
    id: 'rli-1', itemId: 'item-1', itemName: 'Royal Sherwani', itemSize: 'M',
    itemCategory: 'COSTUME', itemDescription: null, quantity: 1, rateSnapshot: 300,
    depositSnapshot: 1000, lineRent: 300, lineDeposit: 1000, itemPurchaseRate: 1500,
  }], ...o,
})

export const aReceiptSummary: Partialize<ReceiptSummary> = (o = {}) => ({
  id: 'rcpt-1', receiptNumber: 'R-2026-0001', customerName: 'Meera', customerPhone: '9811122233',
  itemNames: ['Royal Sherwani ×1'], startDatetime: '2026-04-18T10:00:00+05:30',
  endDatetime: '2026-04-19T10:00:00+05:30', rentalDays: 1, totalRent: 300, totalDeposit: 1000,
  grandTotal: 1300, status: 'GIVEN', isOverdue: false, overdueHours: null, ...o,
})

export const aCheckoutPreview: Partialize<CheckoutPreview> = (o = {}) => ({
  allAvailable: true, rentalDays: 1, totalRent: 300, totalDeposit: 1000, grandTotal: 1300,
  unavailableItems: [], lineItems: [{
    itemId: 'item-1', itemName: 'Royal Sherwani', rate: 300, deposit: 1000, quantity: 1,
    rentalDays: 1, lineRent: 300, lineDeposit: 1000, availableQuantity: 3,
  }], ...o,
})

export const anInvoice: Partialize<Invoice> = (o = {}) => ({
  id: 'inv-1', invoiceNumber: 'INV-2026-0001', shareToken: 'share-i', receiptId: 'rcpt-1',
  receiptNumber: 'R-2026-0001', customerId: 'cust-1', customerName: 'Meera', customerPhone: '9811122233',
  returnDatetime: '2026-04-19T10:00:00+05:30', totalRent: 300, totalDepositCollected: 1000,
  totalLateFee: 0, totalDamageCost: 0, depositToReturn: 1000, finalAmount: 1000,
  transactionType: 'REFUND', paymentMethod: 'CASH', damageNotes: null, notes: null,
  createdAt: '2026-04-19T10:00:00+05:30', lineItems: [{
    id: 'ili-1', itemId: 'item-1', itemName: 'Royal Sherwani', itemSize: 'M', itemCategory: 'COSTUME',
    quantityReturned: 1, rateSnapshot: 300, depositSnapshot: 1000, isDamaged: false,
    damagePercentage: null, damageCost: 0, lateFee: 0,
  }], ...o,
})

export const aReturnPreview: Partialize<ReturnPreview> = (o = {}) => ({
  totalLateFee: 0, totalDamageCost: 0, totalDeductions: 0, depositToReturn: 1000,
  finalAmount: 1000, transactionType: 'REFUND',
  lineItems: [{ receiptLineItemId: 'rli-1', itemName: 'Royal Sherwani', quantity: 1, lateFee: 0, damageCost: 0 }], ...o,
})

export const aDailyRevenue: Partialize<DailyRevenue> = (o = {}) => ({
  date: '2026-04-19', rentCollected: 300, depositsCollected: 1000, depositsRefunded: 0,
  collectedFromCustomers: 0, lateFeeIncome: 0, damageIncome: 0, netFlow: 1300,
  newReceiptsCount: 1, returnsProcessedCount: 0, ...o,
})

export const outstandingDeposits: Partialize<OutstandingDeposits> = (o = {}) => ({
  totalOutstanding: 1000, items: [{
    receiptId: 'rcpt-1', receiptNumber: 'R-2026-0001', customerName: 'Meera', customerPhone: '9811122233',
    itemNames: ['Royal Sherwani ×1'], deposit: 1000, endDatetime: '2026-04-19T10:00:00+05:30', daysSinceRented: 2,
  }], ...o,
})

export const overdueRentals: Partialize<OverdueRentals> = (o = {}) => ({
  overdueCount: 0, items: [], ...o,
})

export const aMonthlyRevenue: Partialize<MonthlyRevenue> = (o = {}) => ({
  year: 2026, month: 4, totalRentCollected: 300, totalDepositsCollected: 1000, totalDepositsRefunded: 0,
  totalCollectedFromCustomers: 0, totalLateFeeIncome: 0, totalDamageIncome: 0, totalNetFlow: 1300,
  dailyBreakdown: [{
    date: '2026-04-19', rentCollected: 300, depositsCollected: 1000, depositsRefunded: 0,
    collectedFromCustomers: 0, lateFeeIncome: 0, damageIncome: 0, netFlow: 1300,
  }], ...o,
})

export const aLateFeeRule: Partialize<LateFeeRule> = (o = {}) => ({
  id: 'rule-1', durationFromHours: 0, durationToHours: 24, penaltyMultiplier: 1.5,
  sortOrder: 0, isActive: true, label: '0–24h', ...o,
})

export const aUserRecord: Partialize<UserRecord> = (o = {}) => ({
  id: 'user-1', username: 'owner', role: 'OWNER', createdAt: '2026-04-10T10:00:00+05:30', ...o,
})

export const aLoginResponse: Partialize<LoginResponse> = (o = {}) => ({
  token: 'test-jwt-token', role: 'OWNER', ...o,
})

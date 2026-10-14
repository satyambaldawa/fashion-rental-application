export interface DailyRevenue {
  date: string
  rentCollected: number
  depositsCollected: number
  depositsRefunded: number
  collectedFromCustomers: number
  lateFeeIncome: number
  damageIncome: number
  totalDiscountsGiven: number
  netFlow: number
  newReceiptsCount: number
  returnsProcessedCount: number
  cancellationRefunds: number
  cancellationsCount: number
}

export interface OutstandingDepositItem {
  receiptId: string
  receiptNumber: string
  customerName: string
  customerPhone: string
  itemNames: string[]
  deposit: number
  endDatetime: string
  daysSinceRented: number
}

export interface OutstandingDeposits {
  totalOutstanding: number
  items: OutstandingDepositItem[]
}

export interface OverdueRentalItem {
  receiptId: string
  receiptNumber: string
  customerName: string
  customerPhone: string
  itemNames: string[]
  endDatetime: string
  overdueHours: number
}

export interface OverdueRentals {
  overdueCount: number
  items: OverdueRentalItem[]
}

export interface DailyRevenueSummary {
  date: string
  rentCollected: number
  depositsCollected: number
  depositsRefunded: number
  collectedFromCustomers: number
  lateFeeIncome: number
  damageIncome: number
  totalDiscountsGiven: number
  netFlow: number
  cancellationRefunds: number
}

export interface MonthlyRevenue {
  year: number
  month: number
  totalRentCollected: number
  totalDepositsCollected: number
  totalDepositsRefunded: number
  totalCollectedFromCustomers: number
  totalLateFeeIncome: number
  totalDamageIncome: number
  totalDiscountsGiven: number
  totalNetFlow: number
  dailyBreakdown: DailyRevenueSummary[]
  totalCancellationRefunds: number
}

export interface CouponDiscountSummary {
  couponCode: string
  timesApplied: number
  totalDiscount: number
}

export interface DiscountsGiven {
  from: string
  to: string
  totalDiscountGiven: number
  receiptsWithCoupon: number
  byCoupon: CouponDiscountSummary[]
}

export interface InvoiceListItem {
  invoiceId: string
  invoiceNumber: string
  invoiceDate: string
  customerName: string
  receiptNumber: string
  items: number
  rent: number
  discount: number
  lateFee: number
  damage: number
  revenue: number
}

export interface InvoicesReport {
  from: string
  to: string
  minRevenue: number | null
  page: number
  pageSize: number
  totalPages: number
  invoiceCount: number
  totalItems: number
  totalRent: number
  totalDiscount: number
  totalLateFee: number
  totalDamage: number
  totalRevenue: number
  invoices: InvoiceListItem[]
}

export interface InvoicesReportQuery {
  from: string
  to: string
  minRevenue?: number
  page: number
  size: number
}

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

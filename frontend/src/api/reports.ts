import client from './client'
import type { ApiResponse } from '../types/api'
import type { DailyRevenue, MonthlyRevenue, OutstandingDeposits, OverdueRentals } from '../types/reports'

export const reportsApi = {
  getDailyRevenue: (date?: string): Promise<DailyRevenue> =>
    client.get<ApiResponse<DailyRevenue>>('/reports/daily-revenue', { params: date ? { date } : undefined })
      .then(r => r.data.data!),

  getOutstandingDeposits: (): Promise<OutstandingDeposits> =>
    client.get<ApiResponse<OutstandingDeposits>>('/reports/outstanding-deposits')
      .then(r => r.data.data!),

  getOverdueRentals: (): Promise<OverdueRentals> =>
    client.get<ApiResponse<OverdueRentals>>('/reports/overdue-rentals')
      .then(r => r.data.data!),

  getMonthlyRevenue: (year: number, month: number): Promise<MonthlyRevenue> =>
    client.get<ApiResponse<MonthlyRevenue>>('/reports/monthly-revenue', { params: { year, month } })
      .then(r => r.data.data!),
}

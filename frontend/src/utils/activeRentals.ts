import dayjs, { type Dayjs } from 'dayjs'
import timezone from 'dayjs/plugin/timezone'
import utc from 'dayjs/plugin/utc'
import type { ReceiptSummary } from '../types/receipt'

dayjs.extend(utc)
dayjs.extend(timezone)

const IST = 'Asia/Kolkata'
const DATE_KEY_FORMAT = 'YYYY-MM-DD'

export interface PickupDateGroup {
  dateKey: string
  label: string
  receipts: ReceiptSummary[]
  isOpenByDefault: boolean
}

export function matchesRentalSearch(receipt: ReceiptSummary, query: string): boolean {
  const needle = query.trim().toLowerCase()
  if (needle === '') return true
  return [receipt.customerName, ...receipt.itemNames].some(text => text.toLowerCase().includes(needle))
}

// Rentals stay under their pickup date until returned, so a past date lingers while anything from it is still out.
export function groupByPickupDate(receipts: ReceiptSummary[], now: Dayjs = dayjs()): PickupDateGroup[] {
  const today = now.tz(IST).startOf('day')
  const byDateKey = new Map<string, ReceiptSummary[]>()

  const sortedByPickup = [...receipts].sort((a, b) => dayjs(a.startDatetime).diff(dayjs(b.startDatetime)))
  for (const receipt of sortedByPickup) {
    const dateKey = dayjs(receipt.startDatetime).tz(IST).format(DATE_KEY_FORMAT)
    byDateKey.set(dateKey, [...(byDateKey.get(dateKey) ?? []), receipt])
  }

  return [...byDateKey.entries()].map(([dateKey, groupReceipts]) => {
    const daysFromToday = dayjs.tz(dateKey, IST).diff(today, 'day')
    const hasOverdue = groupReceipts.some(r => r.isOverdue)
    return {
      dateKey,
      label: pickupDateLabel(dayjs.tz(dateKey, IST), daysFromToday),
      receipts: groupReceipts,
      isOpenByDefault: daysFromToday === 0 || daysFromToday === 1 || hasOverdue,
    }
  })
}

function pickupDateLabel(date: Dayjs, daysFromToday: number): string {
  const dayMonth = date.format('D MMM')
  if (daysFromToday === 0) return `Today · ${dayMonth}`
  if (daysFromToday === 1) return `Tomorrow · ${dayMonth}`
  const weekdayDayMonth = `${date.format('ddd')} · ${dayMonth}`
  return daysFromToday < 0 ? `${weekdayDayMonth} · picked up` : weekdayDayMonth
}

import type { CartItem } from '../../types/receipt'

export const MAX_AD_HOC_QUANTITY = 100

export function lineRentOf(item: CartItem, rentalDays: number): number {
  return item.kind === 'ADHOC'
    ? item.flatPrice * item.quantity
    : item.rate * rentalDays * item.quantity
}

// Mirrors CheckoutService#derivePerDayRate: floors both the divisor and the result so the
// displayed per-day rate never disagrees with what the backend actually persists as rateSnapshot.
export function deriveFlooredPerDayRate(flatPrice: number, rentalDays: number): number {
  const days = Math.max(1, rentalDays)
  return Math.max(1, Math.round(flatPrice / days))
}

export function perDayRateOf(item: CartItem, rentalDays: number): number {
  return item.kind === 'ADHOC' ? deriveFlooredPerDayRate(item.flatPrice, rentalDays) : item.rate
}

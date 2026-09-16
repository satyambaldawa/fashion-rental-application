import type { CartItem } from '../../types/receipt'

export function lineRentOf(item: CartItem, rentalDays: number): number {
  return item.kind === 'ADHOC'
    ? item.flatPrice * item.quantity
    : item.rate * rentalDays * item.quantity
}

export function perDayRateOf(item: CartItem, rentalDays: number): number {
  if (item.kind !== 'ADHOC') return item.rate
  // Mirrors CheckoutService#derivePerDayRate: floors both the divisor and the result so the
  // displayed per-day rate never disagrees with what the backend actually persists as rateSnapshot.
  const days = Math.max(1, rentalDays)
  return Math.max(1, Math.round(item.flatPrice / days))
}

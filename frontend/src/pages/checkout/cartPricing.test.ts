import { describe, it, expect } from 'vitest'
import { lineRentOf, perDayRateOf, deriveFlooredPerDayRate } from './cartPricing'
import type { CatalogueCartItem, AdHocCartItem } from '../../types/receipt'

const aCatalogueItem = (overrides: Partial<CatalogueCartItem> = {}): CatalogueCartItem => ({
  kind: 'CATALOGUE', lineKey: 'item-1', itemId: 'item-1', itemName: 'Sherwani', itemType: 'INDIVIDUAL',
  category: 'COSTUME', size: null, componentNames: null, thumbnailUrl: null, rate: 200,
  deposit: 500, quantity: 1, availableQuantity: 3, ...overrides,
})

const anAdHocItem = (overrides: Partial<AdHocCartItem> = {}): AdHocCartItem => ({
  kind: 'ADHOC', lineKey: 'adhoc-1', itemName: 'Red Sherwani', size: null,
  quantity: 1, deposit: 500, flatPrice: 500, ...overrides,
})

describe('lineRentOf', () => {
  it('prices a catalogue line as rate × days × quantity', () => {
    expect(lineRentOf(aCatalogueItem({ rate: 200, quantity: 2 }), 3)).toBe(1200)
  })

  it('prices an ad-hoc line as flatPrice × quantity, ignoring rentalDays entirely', () => {
    expect(lineRentOf(anAdHocItem({ flatPrice: 500, quantity: 2 }), 3)).toBe(1000)
    expect(lineRentOf(anAdHocItem({ flatPrice: 500, quantity: 2 }), 30)).toBe(1000)
  })
})

describe('perDayRateOf', () => {
  it('returns the catalogue item rate unchanged', () => {
    expect(perDayRateOf(aCatalogueItem({ rate: 200 }), 3)).toBe(200)
  })

  it('derives an ad-hoc rate from flatPrice / rentalDays', () => {
    expect(perDayRateOf(anAdHocItem({ flatPrice: 500 }), 3)).toBe(167)
  })
})

describe('deriveFlooredPerDayRate', () => {
  it('rounds to the nearest rupee', () => {
    expect(deriveFlooredPerDayRate(5, 2)).toBe(3) // round(2.5) = 3
  })

  it('floors a rate that would round down to zero at ₹1, since items.rate has CHECK (rate > 0)', () => {
    expect(deriveFlooredPerDayRate(5, 14)).toBe(1) // round(0.36) = 0, floored to 1
  })

  it('floors the divisor at 1 day, so a zero or negative rentalDays never divides by zero', () => {
    expect(deriveFlooredPerDayRate(500, 0)).toBe(500)
    expect(deriveFlooredPerDayRate(500, -1)).toBe(500)
  })
})

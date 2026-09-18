import { describe, it, expect, beforeEach } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useCart, STORAGE_KEY, LEGACY_STORAGE_KEY } from './useCart'
import type { AppliedCouponPreview, CatalogueCartItem, AdHocCartItem } from '../types/receipt'

const aCouponPreview = (overrides: Partial<AppliedCouponPreview> = {}): AppliedCouponPreview => ({
  couponCode: 'SAVE20', discountAmount: 60, totalRent: 300, totalDeposit: 1000, grandTotal: 1240, ...overrides,
})

const aCatalogueItem = (overrides: Partial<CatalogueCartItem> = {}): CatalogueCartItem => ({
  kind: 'CATALOGUE', lineKey: 'i1', itemId: 'i1', itemName: 'Sherwani', itemType: 'INDIVIDUAL',
  category: 'COSTUME', size: null, componentNames: null, thumbnailUrl: null, rate: 100,
  deposit: 500, quantity: 1, availableQuantity: 3, ...overrides,
})

const anAdHocItem = (overrides: Partial<AdHocCartItem> = {}): AdHocCartItem => ({
  kind: 'ADHOC', lineKey: 'adhoc-1', itemName: 'Red Sherwani', size: null,
  quantity: 1, deposit: 500, flatPrice: 900, ...overrides,
})

describe('useCart', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
  })

  it('creates a cart, adds/increments/updates/removes items, then clears', () => {
    const { result } = renderHook(() => useCart())

    act(() => result.current.createCart('2026-04-18T10:00:00+05:30', '2026-04-19T10:00:00+05:30', 1))
    expect(result.current.cart?.items).toHaveLength(0)

    act(() => result.current.addItem(aCatalogueItem()))
    expect(result.current.cart?.items).toHaveLength(1)

    act(() => result.current.addItem(aCatalogueItem())) // same lineKey → increments
    expect(result.current.cart?.items[0].quantity).toBe(2)

    act(() => result.current.updateQuantity('i1', 5))
    expect(result.current.cart?.items[0].quantity).toBe(5)

    act(() => result.current.removeItem('i1'))
    expect(result.current.cart?.items).toHaveLength(0)

    act(() => result.current.clearCart())
    expect(result.current.cart).toBeNull()
  })

  it('appends ad-hoc items as separate lines even with identical names, and removes only the targeted line', () => {
    const { result } = renderHook(() => useCart())
    act(() => result.current.createCart('s', 'e', 1))

    act(() => result.current.addItem(anAdHocItem({ lineKey: 'adhoc-1', itemName: 'Red Sherwani' })))
    act(() => result.current.addItem(anAdHocItem({ lineKey: 'adhoc-2', itemName: 'Red Sherwani' })))
    expect(result.current.cart?.items).toHaveLength(2)
    expect(result.current.cart?.items.every(i => i.quantity === 1)).toBe(true)

    act(() => result.current.removeItem('adhoc-1'))
    expect(result.current.cart?.items).toHaveLength(1)
    expect(result.current.cart?.items[0].lineKey).toBe('adhoc-2')
  })

  it('persists to and loads from localStorage under the current storage key', () => {
    const first = renderHook(() => useCart())
    act(() => first.result.current.createCart('s', 'e', 2))
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!).rentalDays).toBe(2)

    const second = renderHook(() => useCart())
    expect(second.result.current.cart?.rentalDays).toBe(2)
  })

  it('ignores a v1 cart under the legacy storage key rather than parsing it', () => {
    localStorage.setItem(LEGACY_STORAGE_KEY, JSON.stringify({ startDatetime: 's', endDatetime: 'e', rentalDays: 3, items: [] }))
    const { result } = renderHook(() => useCart())
    expect(result.current.cart).toBeNull()
  })

  it('recovers from corrupt localStorage by starting empty', () => {
    localStorage.setItem(STORAGE_KEY, 'not-json')
    const { result } = renderHook(() => useCart())
    expect(result.current.cart).toBeNull()
  })

  describe('coupon', () => {
    it('applies and removes a coupon', () => {
      const { result } = renderHook(() => useCart())
      act(() => result.current.createCart('s', 'e', 1))
      act(() => result.current.addItem(aCatalogueItem()))

      act(() => result.current.applyCoupon(aCouponPreview()))
      expect(result.current.cart?.appliedCoupon).toEqual(aCouponPreview())

      act(() => result.current.removeCoupon())
      expect(result.current.cart?.appliedCoupon).toBeNull()
    })

    it('clears an applied coupon on addItem, removeItem, and updateQuantity — the discount is a function of the subtotal, so a stale one must never survive a cart edit', () => {
      const { result } = renderHook(() => useCart())
      act(() => result.current.createCart('s', 'e', 1))
      act(() => result.current.addItem(aCatalogueItem()))
      act(() => result.current.applyCoupon(aCouponPreview()))
      expect(result.current.cart?.appliedCoupon).not.toBeNull()

      act(() => result.current.updateQuantity('i1', 2))
      expect(result.current.cart?.appliedCoupon).toBeNull()

      act(() => result.current.applyCoupon(aCouponPreview()))
      act(() => result.current.addItem(anAdHocItem()))
      expect(result.current.cart?.appliedCoupon).toBeNull()

      act(() => result.current.applyCoupon(aCouponPreview()))
      act(() => result.current.removeItem('adhoc-1'))
      expect(result.current.cart?.appliedCoupon).toBeNull()
    })

    it('preserves an applied coupon across a remount within the same browser tab session', () => {
      // CheckoutPage fully unmounts on navigation to routes like /customers/register (a
      // separate page, not a screen within CheckoutPage) and remounts on return — found via
      // manual testing that this silently dropped a just-applied coupon on the single most
      // common mid-checkout detour (registering a walk-in customer), seconds after it was
      // applied. sessionStorage isn't cleared by that round trip, so the two useCart()
      // instances below share the marker exactly as they would across that real navigation.
      const first = renderHook(() => useCart())
      act(() => first.result.current.createCart('s', 'e', 1))
      act(() => first.result.current.addItem(aCatalogueItem()))
      act(() => first.result.current.applyCoupon(aCouponPreview()))
      expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!).appliedCoupon).not.toBeNull()

      const second = renderHook(() => useCart())
      expect(second.result.current.cart?.appliedCoupon).toEqual(aCouponPreview())
      expect(second.result.current.cart?.items).toHaveLength(1)
      expect(second.result.current.droppedCouponCode).toBeNull()
    })

    it('drops an applied coupon on rehydration after the browser tab was actually closed and reopened', () => {
      const first = renderHook(() => useCart())
      act(() => first.result.current.createCart('s', 'e', 1))
      act(() => first.result.current.addItem(aCatalogueItem()))
      act(() => first.result.current.applyCoupon(aCouponPreview()))
      expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!).appliedCoupon).not.toBeNull()

      // sessionStorage is what the browser itself clears when a tab actually closes —
      // simulate that, as opposed to an internal route navigation within the same tab.
      sessionStorage.clear()

      const second = renderHook(() => useCart())
      expect(second.result.current.cart?.appliedCoupon).toBeNull()
      // Rehydration must not silently drop the rest of the cart along with the coupon.
      expect(second.result.current.cart?.items).toHaveLength(1)
      // The drop must be observable, not silent.
      expect(second.result.current.droppedCouponCode).toBe('SAVE20')
    })

    it('reports no dropped coupon when the cart never had one applied', () => {
      const first = renderHook(() => useCart())
      act(() => first.result.current.createCart('s', 'e', 1))
      act(() => first.result.current.addItem(aCatalogueItem()))

      const second = renderHook(() => useCart())
      expect(second.result.current.droppedCouponCode).toBeNull()
    })

    it('is a no-op when the cart was cleared while a coupon preview request was in flight, rather than writing a malformed {appliedCoupon} object to storage', () => {
      // Models the race: a checkout preview response for a coupon resolves after the staff
      // member has already deleted the cart. Without a null guard, applyCoupon's `cart!`
      // silently spreads `null` into `{}`, and setCart persists an object with an
      // appliedCoupon but no items/dates — which crashes every subsequent render that reads
      // cart.items.
      const { result } = renderHook(() => useCart())
      act(() => result.current.createCart('s', 'e', 1))
      act(() => result.current.clearCart())
      expect(result.current.cart).toBeNull()

      act(() => result.current.applyCoupon(aCouponPreview()))
      expect(result.current.cart).toBeNull()
      expect(localStorage.getItem(STORAGE_KEY)).toBeNull()

      act(() => result.current.removeCoupon())
      expect(result.current.cart).toBeNull()
      expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
    })
  })
})

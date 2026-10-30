import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { act, render, renderHook } from '@testing-library/react'
import { createElement, useEffect } from 'react'
import {
  useCart, STORAGE_KEY, LEGACY_STORAGE_KEY, SESSION_MARKER_KEY, STALE_CART_THRESHOLD_MS,
  isCartStale, peekPersistedCart, startCustomerCart,
} from './useCart'
import type { AppliedCouponPreview, Cart, CartCustomer, CatalogueCartItem, AdHocCartItem } from '../types/receipt'

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

  describe('customer carried over from a receipt (#165)', () => {
    const customer: CartCustomer = { id: 'cust-9', name: 'Priya', phone: '9900011122' }

    it('persists the customer through every mutator, until clearCart drops it', () => {
      startCustomerCart({ customer, startDatetime: 's', endDatetime: 'e', rentalDays: 1 })
      const { result } = renderHook(() => useCart())
      expect(result.current.cart?.customer).toEqual(customer)

      act(() => result.current.addItem(aCatalogueItem()))
      expect(result.current.cart?.customer).toEqual(customer)

      act(() => result.current.updateQuantity('i1', 3))
      expect(result.current.cart?.customer).toEqual(customer)

      act(() => result.current.applyCoupon(aCouponPreview()))
      expect(result.current.cart?.customer).toEqual(customer)

      act(() => result.current.removeCoupon())
      expect(result.current.cart?.customer).toEqual(customer)

      act(() => result.current.removeItem('i1'))
      expect(result.current.cart?.customer).toEqual(customer)

      act(() => result.current.clearCart())
      expect(result.current.cart).toBeNull()
    })

    it('loads an old-format cart with no customer field as undefined, leaving existing behaviour unchanged', () => {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        startDatetime: 's', endDatetime: 'e', rentalDays: 1, items: [],
        lastChangedAt: new Date(Date.now() - 30 * 60 * 1000).toISOString(),
      }))

      const { result } = renderHook(() => useCart())

      expect(result.current.cart?.customer).toBeUndefined()
      expect(result.current.cart?.items).toEqual([])
    })

    it('falls back to null when a malformed customer is found in storage', () => {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        startDatetime: 's', endDatetime: 'e', rentalDays: 1, items: [], customer: {},
        lastChangedAt: new Date(Date.now() - 30 * 60 * 1000).toISOString(),
      }))

      const { result } = renderHook(() => useCart())

      expect(result.current.cart?.customer).toBeNull()
    })

    it('peekPersistedCart reads without marking the session, and startCustomerCart writes the expected shape', () => {
      expect(peekPersistedCart()).toBeNull()
      expect(sessionStorage.getItem(SESSION_MARKER_KEY)).toBeNull()

      startCustomerCart({ customer, startDatetime: 's', endDatetime: 'e', rentalDays: 2 })

      expect(sessionStorage.getItem(SESSION_MARKER_KEY)).toBeNull()
      expect(peekPersistedCart()).toEqual({
        startDatetime: 's', endDatetime: 'e', rentalDays: 2, items: [], appliedCoupon: null, customer,
        lastChangedAt: expect.any(String),
      })
    })
  })

  describe('stale cart expiry (#172)', () => {
    const customer: CartCustomer = { id: 'cust-9', name: 'Priya', phone: '9900011122' }

    function seedStored(cart: unknown) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(cart))
    }

    afterEach(() => {
      vi.useRealTimers()
    })

    describe('isCartStale', () => {
      const now = Date.parse('2026-04-18T12:00:00.000Z')
      const cartAt = (lastChangedAt?: string): Cart => (
        { startDatetime: 's', endDatetime: 'e', rentalDays: 1, items: [], lastChangedAt }
      )

      it('treats a cart younger than the threshold as fresh', () => {
        const changedAt = new Date(now - 59 * 60 * 1000).toISOString()
        expect(isCartStale(cartAt(changedAt), now)).toBe(false)
      })

      it('treats a cart exactly at the threshold as fresh (boundary)', () => {
        const changedAt = new Date(now - STALE_CART_THRESHOLD_MS).toISOString()
        expect(isCartStale(cartAt(changedAt), now)).toBe(false)
      })

      it('treats a cart one millisecond past the threshold as stale', () => {
        const changedAt = new Date(now - STALE_CART_THRESHOLD_MS - 1).toISOString()
        expect(isCartStale(cartAt(changedAt), now)).toBe(true)
      })

      it('treats a cart hours old as stale', () => {
        const changedAt = new Date(now - 3 * 60 * 60 * 1000).toISOString()
        expect(isCartStale(cartAt(changedAt), now)).toBe(true)
      })

      it('treats a missing lastChangedAt as stale', () => {
        expect(isCartStale(cartAt(undefined), now)).toBe(true)
      })

      it('treats an unparseable lastChangedAt as stale', () => {
        expect(isCartStale(cartAt('garbage'), now)).toBe(true)
      })
    })

    describe('useCart hook', () => {
      it('clears a stale cart on mount, dropping its coupon too', () => {
        const T0 = Date.parse('2026-04-18T12:00:00.000Z')
        vi.useFakeTimers({ toFake: ['Date'] })
        vi.setSystemTime(T0)
        seedStored({
          startDatetime: 's', endDatetime: 'e', rentalDays: 1,
          items: [aCatalogueItem()],
          customer,
          appliedCoupon: aCouponPreview(),
          lastChangedAt: new Date(T0 - 61 * 60 * 1000).toISOString(),
        })

        const { result } = renderHook(() => useCart())

        expect(result.current.cart).toBeNull()
        expect(result.current.droppedCouponCode).toBeNull()
        expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
      })

      it('restores a fresh cart unchanged, with storage untouched', () => {
        const T0 = Date.parse('2026-04-18T12:00:00.000Z')
        vi.useFakeTimers({ toFake: ['Date'] })
        vi.setSystemTime(T0)
        sessionStorage.setItem(SESSION_MARKER_KEY, '1')
        const stored = {
          startDatetime: 's', endDatetime: 'e', rentalDays: 1,
          items: [aCatalogueItem()],
          customer,
          appliedCoupon: aCouponPreview(),
          lastChangedAt: new Date(T0 - 30 * 60 * 1000).toISOString(),
        }
        seedStored(stored)

        const { result } = renderHook(() => useCart())

        expect(result.current.cart?.items).toHaveLength(1)
        expect(result.current.cart?.customer).toEqual(customer)
        expect(result.current.cart?.startDatetime).toBe('s')
        expect(result.current.cart?.endDatetime).toBe('e')
        expect(result.current.cart?.appliedCoupon).toEqual(aCouponPreview())
        expect(localStorage.getItem(STORAGE_KEY)).toBe(JSON.stringify(stored))
      })

      it('a fresh cart in a new tab still drops the coupon and reports droppedCouponCode', () => {
        const T0 = Date.parse('2026-04-18T12:00:00.000Z')
        vi.useFakeTimers({ toFake: ['Date'] })
        vi.setSystemTime(T0)
        seedStored({
          startDatetime: 's', endDatetime: 'e', rentalDays: 1,
          items: [aCatalogueItem()],
          appliedCoupon: aCouponPreview(),
          lastChangedAt: new Date(T0 - 30 * 60 * 1000).toISOString(),
        })

        const { result } = renderHook(() => useCart())

        expect(result.current.cart?.appliedCoupon).toBeNull()
        expect(result.current.droppedCouponCode).toBe('SAVE20')
      })

      it('discards a cart with no lastChangedAt at all', () => {
        seedStored({ startDatetime: 's', endDatetime: 'e', rentalDays: 1, items: [aCatalogueItem()] })

        const { result } = renderHook(() => useCart())

        expect(result.current.cart).toBeNull()
        expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
      })

      it('refreshes lastChangedAt on every mutator', () => {
        const T0 = Date.parse('2026-04-18T12:00:00.000Z')
        vi.useFakeTimers({ toFake: ['Date'] })
        vi.setSystemTime(T0)

        const { result } = renderHook(() => useCart())

        act(() => result.current.createCart('s', 'e', 1))
        expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!).lastChangedAt).toBe(new Date(T0).toISOString())

        const T1 = T0 + 10 * 60 * 1000
        vi.setSystemTime(T1)
        act(() => result.current.addItem(aCatalogueItem()))
        expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!).lastChangedAt).toBe(new Date(T1).toISOString())
        expect(result.current.cart?.lastChangedAt).toBe(new Date(T1).toISOString())

        const T2 = T1 + 10 * 60 * 1000
        vi.setSystemTime(T2)
        act(() => result.current.updateQuantity('i1', 2))
        expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!).lastChangedAt).toBe(new Date(T2).toISOString())

        const T3 = T2 + 10 * 60 * 1000
        vi.setSystemTime(T3)
        act(() => result.current.applyCoupon(aCouponPreview()))
        expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!).lastChangedAt).toBe(new Date(T3).toISOString())

        const T4 = T3 + 10 * 60 * 1000
        vi.setSystemTime(T4)
        act(() => result.current.removeCoupon())
        expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!).lastChangedAt).toBe(new Date(T4).toISOString())

        const T5 = T4 + 10 * 60 * 1000
        vi.setSystemTime(T5)
        act(() => result.current.removeItem('i1'))
        expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!).lastChangedAt).toBe(new Date(T5).toISOString())
      })

      it('keeps a cart alive past the threshold when it was edited recently', () => {
        const T0 = Date.parse('2026-04-18T12:00:00.000Z')
        vi.useFakeTimers({ toFake: ['Date'] })
        vi.setSystemTime(T0)

        const first = renderHook(() => useCart())
        act(() => first.result.current.createCart('s', 'e', 1))

        const editTime = T0 + 2 * 60 * 60 * 1000 + 50 * 60 * 1000 // T0 + 2h50m
        vi.setSystemTime(editTime)
        act(() => first.result.current.addItem(aCatalogueItem()))

        vi.setSystemTime(T0 + 3 * 60 * 60 * 1000) // T0 + 3h
        const second = renderHook(() => useCart())

        expect(second.result.current.cart?.items).toHaveLength(1)
        expect(localStorage.getItem(STORAGE_KEY)).not.toBeNull()
      })

      it('a cart seeded by startCustomerCart (#165) is never treated as stale', () => {
        const T0 = Date.parse('2026-04-18T12:00:00.000Z')
        vi.useFakeTimers({ toFake: ['Date'] })
        vi.setSystemTime(T0)

        startCustomerCart({ customer, startDatetime: 's', endDatetime: 'e', rentalDays: 1 })
        expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!).lastChangedAt).toBe(new Date(T0).toISOString())

        const { result } = renderHook(() => useCart())

        expect(result.current.cart?.customer).toEqual(customer)
        expect(result.current.cart?.startDatetime).toBe('s')
        expect(result.current.cart?.endDatetime).toBe('e')
        expect(localStorage.getItem(STORAGE_KEY)).not.toBeNull()
      })

      it('startCustomerCart overwrites an already-stale cart with the freshly seeded one', () => {
        seedStored({
          startDatetime: 'old-s', endDatetime: 'old-e', rentalDays: 1, items: [aCatalogueItem()],
          lastChangedAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
        })

        startCustomerCart({ customer, startDatetime: 's', endDatetime: 'e', rentalDays: 1 })
        const { result } = renderHook(() => useCart())

        expect(result.current.cart).not.toBeNull()
        expect(result.current.cart?.customer).toEqual(customer)
        expect(result.current.cart?.items).toEqual([])
      })

      it('a short detour within the hour keeps the cart and coupon intact', () => {
        const T0 = Date.parse('2026-04-18T12:00:00.000Z')
        vi.useFakeTimers({ toFake: ['Date'] })
        vi.setSystemTime(T0)

        const first = renderHook(() => useCart())
        act(() => first.result.current.createCart('s', 'e', 1))
        act(() => first.result.current.addItem(aCatalogueItem()))
        act(() => first.result.current.applyCoupon(aCouponPreview()))
        first.unmount()

        vi.setSystemTime(T0 + 20 * 60 * 1000)
        const second = renderHook(() => useCart())

        expect(second.result.current.cart?.items).toHaveLength(1)
        expect(second.result.current.cart?.appliedCoupon).toEqual(aCouponPreview())
        expect(second.result.current.droppedCouponCode).toBeNull()
      })

      it('variant: a detour past the hour with no edits discards the cart', () => {
        const T0 = Date.parse('2026-04-18T12:00:00.000Z')
        vi.useFakeTimers({ toFake: ['Date'] })
        vi.setSystemTime(T0)

        const first = renderHook(() => useCart())
        act(() => first.result.current.createCart('s', 'e', 1))
        act(() => first.result.current.addItem(aCatalogueItem()))
        act(() => first.result.current.applyCoupon(aCouponPreview()))
        first.unmount()

        vi.setSystemTime(T0 + 61 * 60 * 1000)
        const second = renderHook(() => useCart())

        expect(second.result.current.cart).toBeNull()
      })

      it('peekPersistedCart does not filter stale carts; only mounting useCart clears storage', () => {
        seedStored({
          startDatetime: 's', endDatetime: 'e', rentalDays: 1, items: [aCatalogueItem()],
          lastChangedAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
        })

        expect(peekPersistedCart()?.items).toHaveLength(1)
        expect(localStorage.getItem(STORAGE_KEY)).not.toBeNull()

        renderHook(() => useCart())

        expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
      })

      it('does not delete a cart that was rewritten fresh by a child effect before the removal effect runs (the double-check race guard)', () => {
        // Proves the removal effect's second staleness check actually does something, not
        // just that removal eventually happens. React fires a child component's effects
        // before its parent's own effects, so RaceChild's write below lands in storage
        // before useCart's removal effect (owned by Harness, the parent) runs — reproducing
        // the exact race the comment on that effect describes: "a fresh cart written since
        // then (a child effect ...) is never deleted." Without the re-check (e.g. if the
        // effect unconditionally called saveCart(null) whenever discardedStale is true),
        // this freshly-written cart would be wiped out; with it, it survives.
        const T0 = Date.parse('2026-04-18T12:00:00.000Z')
        vi.useFakeTimers({ toFake: ['Date'] })
        vi.setSystemTime(T0)
        seedStored({
          startDatetime: 's', endDatetime: 'e', rentalDays: 1, items: [aCatalogueItem()],
          lastChangedAt: new Date(T0 - 61 * 60 * 1000).toISOString(),
        })

        const freshCart: Cart = {
          startDatetime: 'fresh-s', endDatetime: 'fresh-e', rentalDays: 2, items: [],
          lastChangedAt: new Date(T0).toISOString(),
        }

        function RaceChild() {
          useEffect(() => {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(freshCart))
          }, [])
          return null
        }

        function Harness() {
          useCart()
          return createElement(RaceChild)
        }

        act(() => {
          render(createElement(Harness))
        })

        expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toEqual(freshCart)
      })
    })
  })
})

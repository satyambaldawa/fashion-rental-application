import { describe, it, expect, beforeEach } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useCart, STORAGE_KEY, LEGACY_STORAGE_KEY } from './useCart'
import type { CatalogueCartItem, AdHocCartItem } from '../types/receipt'

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
  beforeEach(() => localStorage.clear())

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
})

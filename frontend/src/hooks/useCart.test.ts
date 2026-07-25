import { describe, it, expect, beforeEach } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useCart } from './useCart'
import type { CartItem } from '../types/receipt'

const anItem = (overrides: Partial<CartItem> = {}): CartItem => ({
  itemId: 'i1', itemName: 'Sherwani', itemType: 'INDIVIDUAL', category: 'COSTUME',
  size: null, componentNames: null, thumbnailUrl: null, rate: 100, deposit: 500,
  quantity: 1, availableQuantity: 3, ...overrides,
})

describe('useCart', () => {
  beforeEach(() => localStorage.clear())

  it('creates a cart, adds/increments/updates/removes items, then clears', () => {
    const { result } = renderHook(() => useCart())

    act(() => result.current.createCart('2026-04-18T10:00:00+05:30', '2026-04-19T10:00:00+05:30', 1))
    expect(result.current.cart?.items).toHaveLength(0)

    act(() => result.current.addItem(anItem()))
    expect(result.current.cart?.items).toHaveLength(1)

    act(() => result.current.addItem(anItem())) // same item → increments
    expect(result.current.cart?.items[0].quantity).toBe(2)

    act(() => result.current.updateQuantity('i1', 5))
    expect(result.current.cart?.items[0].quantity).toBe(5)

    act(() => result.current.removeItem('i1'))
    expect(result.current.cart?.items).toHaveLength(0)

    act(() => result.current.clearCart())
    expect(result.current.cart).toBeNull()
  })

  it('persists to and loads from localStorage', () => {
    const first = renderHook(() => useCart())
    act(() => first.result.current.createCart('s', 'e', 2))
    expect(JSON.parse(localStorage.getItem('rental_cart')!).rentalDays).toBe(2)

    const second = renderHook(() => useCart())
    expect(second.result.current.cart?.rentalDays).toBe(2)
  })

  it('recovers from corrupt localStorage by starting empty', () => {
    localStorage.setItem('rental_cart', 'not-json')
    const { result } = renderHook(() => useCart())
    expect(result.current.cart).toBeNull()
  })
})

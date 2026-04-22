import { useState, useCallback } from 'react'

export interface CartItem {
  itemId: string
  itemName: string
  thumbnailUrl: string | null
  rate: number
  deposit: number
  quantity: number
  availableQuantity: number
}

export interface Cart {
  startDatetime: string   // ISO 8601
  endDatetime: string     // ISO 8601
  rentalDays: number
  items: CartItem[]
}

const STORAGE_KEY = 'rental_cart'

function loadCart(): Cart | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as Cart) : null
  } catch {
    return null
  }
}

function saveCart(cart: Cart | null) {
  if (cart) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(cart))
  } else {
    localStorage.removeItem(STORAGE_KEY)
  }
}

export function useCart() {
  const [cart, setCartState] = useState<Cart | null>(loadCart)

  const setCart = useCallback((next: Cart | null) => {
    saveCart(next)
    setCartState(next)
  }, [])

  const createCart = useCallback((startDatetime: string, endDatetime: string, rentalDays: number) => {
    setCart({ startDatetime, endDatetime, rentalDays, items: [] })
  }, [setCart])

  const addItem = useCallback((item: CartItem) => {
    setCart({
      ...cart!,
      items: cart!.items.some(i => i.itemId === item.itemId)
        ? cart!.items.map(i => i.itemId === item.itemId ? { ...i, quantity: i.quantity + 1 } : i)
        : [...cart!.items, item],
    })
  }, [cart, setCart])

  const removeItem = useCallback((itemId: string) => {
    setCart({ ...cart!, items: cart!.items.filter(i => i.itemId !== itemId) })
  }, [cart, setCart])

  const updateQuantity = useCallback((itemId: string, quantity: number) => {
    setCart({ ...cart!, items: cart!.items.map(i => i.itemId === itemId ? { ...i, quantity } : i) })
  }, [cart, setCart])

  const clearCart = useCallback(() => {
    setCart(null)
  }, [setCart])

  return { cart, createCart, addItem, removeItem, updateQuantity, clearCart }
}

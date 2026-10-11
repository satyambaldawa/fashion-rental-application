import { useState, useCallback, useEffect } from 'react'
import type { AppliedCouponPreview, Cart, CartItem, CustomerCartSeed } from '../types/receipt'

export type { Cart, CartItem }

export const STORAGE_KEY = 'rental_cart_v2'
export const LEGACY_STORAGE_KEY = 'rental_cart'
// sessionStorage (unlike localStorage) survives page navigations and reloads within one
// browser tab but is cleared when the tab closes — exactly the boundary that matters for
// coupon trust. See loadCart() below for why that boundary, not "every component mount",
// is what should invalidate an applied coupon.
export const SESSION_MARKER_KEY = 'rental_cart_session_marker'

// A saved cart untouched for longer than this is discarded when New Rental is opened (#172).
// Not user-configurable by design.
export const STALE_CART_THRESHOLD_MS = 60 * 60 * 1000

interface LoadedCart {
  cart: Cart | null
  // Non-null only on the render where loadCart() itself dropped a coupon that was present
  // in storage — lets the caller warn once, the same way a mid-session cart edit does.
  droppedCouponCode: string | null
}

export function isCartStale(cart: Cart, now: number = Date.now()): boolean {
  const changedAt = cart.lastChangedAt ? Date.parse(cart.lastChangedAt) : NaN
  // Written as "not fresh" so a missing or unparseable timestamp (NaN) counts as stale.
  return !(now - changedAt <= STALE_CART_THRESHOLD_MS)
}

function hasMalformedCustomer(cart: Cart): boolean {
  return !!cart.customer && typeof cart.customer.id !== 'string'
}

function loadCart(): LoadedCart {
  // CheckoutPage fully unmounts on navigation to routes like /customers/register (a
  // separate page, not a screen within CheckoutPage) and remounts on return, so a brand
  // new useCart() instance — and a fresh loadCart() call — runs on every such trip. Found
  // via manual testing: treating that as equivalent to "reopened after being closed"
  // silently dropped a just-applied coupon on the single most common mid-checkout detour
  // (registering a walk-in customer), seconds after it was applied. The sessionStorage
  // marker distinguishes the two: still set → same tab, trust the coupon; absent → the tab
  // was actually closed and reopened (or this is a hard refresh after a long gap), so the
  // coupon's state may genuinely have changed server-side since — force a re-apply.
  // createReceipt() re-validates server-side either way, so this is a display-trust
  // window, not a correctness guarantee.
  //
  // This function only ever *reads* the marker — the write lives in useCart's effect
  // below, deliberately never here. React may call a useState lazy initializer more than
  // once for a single mount (StrictMode, in development), and if the write happened here,
  // the second call would see the first call's own write and wrongly conclude "not new."
  // Reading in render and writing in an effect means every read for a given mount sees
  // whatever the marker was *before* that mount started — including a real earlier mount's
  // effect having already run — with no risk of a mount ever observing its own write.
  const isNewBrowserSession = !sessionStorage.getItem(SESSION_MARKER_KEY)

  try {
    localStorage.removeItem(LEGACY_STORAGE_KEY) // pre-union cart shape; never parsed, just cleared
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { cart: null, droppedCouponCode: null }
    const cart = JSON.parse(raw) as Cart
    // Guards against a malformed cart ever reaching a render — e.g. one written by a
    // coupon mutation racing a clearCart() (see applyCoupon/removeCoupon below).
    if (!Array.isArray(cart.items)) return { cart: null, droppedCouponCode: null }

    const loaded = hasMalformedCustomer(cart) ? { ...cart, customer: null } : cart

    if (loaded.appliedCoupon && isNewBrowserSession) {
      return { cart: { ...loaded, appliedCoupon: null }, droppedCouponCode: loaded.appliedCoupon.couponCode }
    }
    return { cart: loaded, droppedCouponCode: null }
  } catch {
    return { cart: null, droppedCouponCode: null }
  }
}

function saveCart(cart: Cart | null): Cart | null {
  if (!cart) {
    localStorage.removeItem(STORAGE_KEY)
    return null
  }
  // Stamped here, the one place every mutator writes through, so "last changed" always
  // reflects a real cart edit without each mutator having to remember to set it (#172).
  const stamped = { ...cart, lastChangedAt: new Date().toISOString() }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(stamped))
  return stamped
}

// Deliberately plain functions, not hooks: mounting useCart() from another page would write
// SESSION_MARKER_KEY, and CheckoutPage's later loadCart() would then trust a stored coupon
// it should have dropped on a fresh tab.
export function peekPersistedCart(): Cart | null {
  return loadCart().cart
}

export function startCustomerCart(seed: CustomerCartSeed): void {
  saveCart({
    startDatetime: seed.startDatetime,
    endDatetime: seed.endDatetime,
    rentalDays: seed.rentalDays,
    items: [],
    appliedCoupon: null,
    customer: seed.customer,
  })
}

interface InitialCart extends LoadedCart {
  // True only when loadCartForNewRental() itself discarded a stale cart — lets the effect
  // below know it still needs to clear storage, without re-running the staleness check on
  // every render.
  discardedStale: boolean
}

// Pure: reads the clock but writes nothing, so it stays safe as a useState lazy initializer
// (React may call it more than once per mount under StrictMode). The actual storage removal
// happens in the effect below, not here — see loadCart()'s isNewBrowserSession comment for
// why a lazy initializer must never write.
function loadCartForNewRental(): InitialCart {
  const loaded = loadCart()
  if (loaded.cart && isCartStale(loaded.cart)) {
    // A discarded cart shows as empty. droppedCouponCode is forced to null so no
    // "coupon not carried over" warning appears for a cart that no longer exists.
    return { cart: null, droppedCouponCode: null, discardedStale: true }
  }
  return { ...loaded, discardedStale: false }
}

export function useCart() {
  const [{ cart: initialCart, droppedCouponCode, discardedStale }] = useState(loadCartForNewRental)
  const [cart, setCartState] = useState<Cart | null>(initialCart)

  // Marks this browser tab session as touched, for the next mount's loadCart() to see.
  // Deliberately in an effect (runs after render commits) rather than inside loadCart's
  // lazy initializer (runs during render, possibly more than once under StrictMode) — see
  // the comment on isNewBrowserSession in loadCart() for why the two must never share code.
  useEffect(() => {
    sessionStorage.setItem(SESSION_MARKER_KEY, '1')
  }, [])

  // Removes the stale cart from storage. Done here, not in the lazy initializer above,
  // for the same reason as the session marker: the initializer can run twice per mount
  // under StrictMode and must stay a pure read. The stored cart is checked again before
  // removal, so a fresh cart written since then (a child effect or a fast first edit)
  // is never deleted. Running twice under StrictMode is also safe.
  useEffect(() => {
    if (!discardedStale) return
    const stored = loadCart().cart
    if (stored && isCartStale(stored)) saveCart(null)
  }, [discardedStale])

  const setCart = useCallback((next: Cart | null) => {
    setCartState(saveCart(next))
  }, [])

  const createCart = useCallback((startDatetime: string, endDatetime: string, rentalDays: number) => {
    setCart({ startDatetime, endDatetime, rentalDays, items: [] })
  }, [setCart])

  // Any cart mutation invalidates an applied coupon — the discount is a function of the
  // subtotal, so a stale discount must never be displayed. createReceipt() recomputes
  // server-side regardless, so a stale client number could only ever be *shown*, never
  // *charged* — but a customer quoted the wrong discount is still a counter argument the
  // shop doesn't want.
  const addItem = useCallback((item: CartItem) => {
    setCart({
      ...cart!,
      items: cart!.items.some(i => i.lineKey === item.lineKey)
        ? cart!.items.map(i => i.lineKey === item.lineKey ? { ...i, quantity: i.quantity + 1 } : i)
        : [...cart!.items, item],
      appliedCoupon: null,
    })
  }, [cart, setCart])

  const removeItem = useCallback((lineKey: string) => {
    setCart({ ...cart!, items: cart!.items.filter(i => i.lineKey !== lineKey), appliedCoupon: null })
  }, [cart, setCart])

  const updateQuantity = useCallback((lineKey: string, quantity: number) => {
    setCart({
      ...cart!,
      items: cart!.items.map(i => i.lineKey === lineKey ? { ...i, quantity } : i),
      appliedCoupon: null,
    })
  }, [cart, setCart])

  // Guarded (unlike the mutators above) because a coupon-apply request is async: the cart
  // can legitimately be cleared while the request is still in flight, and writing through a
  // null cart here would persist a malformed {appliedCoupon} object with no items/dates to
  // localStorage — corrupting every future load until storage is cleared by hand.
  const applyCoupon = useCallback((preview: AppliedCouponPreview) => {
    if (!cart) return
    setCart({ ...cart, appliedCoupon: preview })
  }, [cart, setCart])

  const removeCoupon = useCallback(() => {
    if (!cart) return
    setCart({ ...cart, appliedCoupon: null })
  }, [cart, setCart])

  const clearCart = useCallback(() => {
    setCart(null)
  }, [setCart])

  return {
    cart, createCart, addItem, removeItem, updateQuantity, applyCoupon, removeCoupon, clearCart,
    droppedCouponCode,
  }
}

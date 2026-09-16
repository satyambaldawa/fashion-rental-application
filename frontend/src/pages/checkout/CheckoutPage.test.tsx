import { describe, it, expect, afterEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { renderWithProviders, flush, screen, within } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import CheckoutPage from './CheckoutPage'
import { useAuthStore } from '../../store/authStore'
import type { Cart, CartItem, CatalogueCartItem, AdHocCartItem } from '../../types/receipt'

function jwtWithRole(role: string): string {
  const encode = (obj: object) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${encode({ alg: 'HS256' })}.${encode({ sub: 'user', role, exp: 9999999999 })}.signature`
}

function setAuth(role: 'OWNER' | 'EXECUTIVE') {
  useAuthStore.setState({ token: jwtWithRole(role), role })
}

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })
const page = (content: unknown[]) => ({
  content, totalElements: content.length, totalPages: 1, number: 0, size: 20,
})

const CART_STORAGE_KEY = 'rental_cart_v2'

const baseCartItem: CatalogueCartItem = {
  kind: 'CATALOGUE',
  lineKey: 'item-1',
  itemId: 'item-1',
  itemName: 'Royal Sherwani',
  itemType: 'INDIVIDUAL',
  category: 'COSTUME',
  size: 'M',
  componentNames: null,
  thumbnailUrl: null,
  rate: 300,
  deposit: 1000,
  quantity: 1,
  availableQuantity: 3,
}

function seedCart(items: CartItem[], overrides: Partial<Pick<Cart, 'rentalDays' | 'endDatetime'>> = {}) {
  const cart: Cart = {
    startDatetime: '2026-04-18T10:00:00+05:30',
    endDatetime: overrides.endDatetime ?? '2026-04-19T10:00:00+05:30',
    rentalDays: overrides.rentalDays ?? 1,
    items,
  }
  localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(cart))
}

// CheckoutPage reads the cart from localStorage on mount, so the fixture must be
// seeded before renderWithProviders is called. The preview table (and its thumbnail
// fallback logic) only mounts once the user advances past the browse screen.
async function goToPreview() {
  const user = userEvent.setup()
  renderWithProviders(<CheckoutPage />)
  await flush()
  await user.click(await screen.findByRole('button', { name: 'Checkout' }))
  await flush()
}

describe('CheckoutPage preview thumbnails', () => {
  afterEach(() => {
    localStorage.removeItem(CART_STORAGE_KEY)
  })

  it('renders the img from the cart item thumbnailUrl when present', async () => {
    seedCart([{ ...baseCartItem, thumbnailUrl: 'https://r2.example/cart-thumb.jpg' }])
    server.use(
      http.get('*/api/items', () => ok(page([
        f.anItemSummary({ id: 'item-1', thumbnailUrl: 'https://r2.example/fresh-thumb.jpg' }),
      ]))),
    )

    await goToPreview()

    const img = await screen.findByRole('img', { name: 'Royal Sherwani' })
    expect(img).toHaveAttribute('src', 'https://r2.example/cart-thumb.jpg')
  })

  it('falls back to the freshly fetched item thumbnail when a stale cart entry has none', async () => {
    seedCart([{ ...baseCartItem, thumbnailUrl: null }])
    server.use(
      http.get('*/api/items', () => ok(page([
        f.anItemSummary({ id: 'item-1', thumbnailUrl: 'https://r2.example/fresh-thumb.jpg' }),
      ]))),
    )

    await goToPreview()

    const img = await screen.findByRole('img', { name: 'Royal Sherwani' })
    expect(img).toHaveAttribute('src', 'https://r2.example/fresh-thumb.jpg')
  })

  it('renders the placeholder when neither the cart item nor the fresh item has a thumbnail', async () => {
    seedCart([{ ...baseCartItem, thumbnailUrl: null }])
    server.use(
      http.get('*/api/items', () => ok(page([
        f.anItemSummary({ id: 'item-1', thumbnailUrl: null }),
      ]))),
    )

    await goToPreview()

    const nameCell = await screen.findByText('Royal Sherwani')
    const itemCell = nameCell.closest('td')!
    expect(within(itemCell).queryByRole('img')).not.toBeInTheDocument()
    expect(itemCell.querySelector('svg')).toBeInTheDocument()
  })
})

describe('CheckoutPage mixed cart pricing', () => {
  afterEach(() => {
    localStorage.removeItem(CART_STORAGE_KEY)
  })

  it('totals a mixed cart as catalogue rate×days×qty plus ad-hoc flat×qty, never rate×days for the ad-hoc line', async () => {
    const adHocItem: AdHocCartItem = {
      kind: 'ADHOC', lineKey: 'adhoc-1', itemName: 'Custom Lehenga', size: null,
      quantity: 2, deposit: 200, flatPrice: 500,
    }
    // 3-day rental so the two candidate formulas diverge:
    // catalogue: rate 300 × 3 days × qty 1 = 900. ad-hoc, correct: flatPrice 500 × qty 2 = 1000 → total 1,900.
    // ad-hoc, WRONG (treating flatPrice as a per-day rate, 500 × 3 days × qty 2 = 3000) → total 3,900.
    // At 1 day these two formulas coincide (500×1×2 = 500×2), so this must not use a 1-day cart.
    seedCart([{ ...baseCartItem }, adHocItem], { rentalDays: 3, endDatetime: '2026-04-21T10:00:00+05:30' })
    server.use(
      http.get('*/api/items', () => ok(page([f.anItemSummary({ id: 'item-1' })]))),
    )

    await goToPreview()

    expect(await screen.findByText('Custom Lehenga')).toBeInTheDocument()
    expect(screen.getByText('₹1,900')).toBeInTheDocument()
    expect(screen.queryByText('₹3,900')).not.toBeInTheDocument()
  })
})

describe('CheckoutPage custom product entry', () => {
  afterEach(() => {
    localStorage.removeItem(CART_STORAGE_KEY)
    useAuthStore.setState({ token: null, role: null })
  })

  it('submits catalogue and ad-hoc lines as correctly shaped separate lists', async () => {
    setAuth('OWNER')
    const adHocItem: AdHocCartItem = {
      kind: 'ADHOC', lineKey: 'adhoc-1', itemName: 'Custom Lehenga', size: 'Free size',
      quantity: 2, deposit: 200, flatPrice: 500,
    }
    seedCart([{ ...baseCartItem }, adHocItem])

    let capturedBody: unknown = null
    server.use(
      http.get('*/api/customers/cust-1', () => ok(f.aCustomer({ id: 'cust-1' }))),
      http.post('*/api/receipts', async ({ request }) => {
        capturedBody = await request.json()
        return ok(f.aReceipt())
      }),
    )

    // path is given so a post-create navigate() unmounts CheckoutPage the same way <Routes>
    // does in the real app -- without it, nothing intercepts the route change and CheckoutPage
    // re-renders on the now-cart-less 'customer' screen instead of being swapped out.
    const user = userEvent.setup()
    renderWithProviders(<CheckoutPage />, { route: '/checkout?newCustomerId=cust-1', path: '/checkout' })
    await flush()

    await user.click(await screen.findByRole('button', { name: 'Create Receipt' }))
    await flush()

    expect(capturedBody).toMatchObject({
      items: [{ itemId: 'item-1', quantity: 1 }],
      adHocItems: [{ name: 'Custom Lehenga', size: 'Free size', flatPrice: 500, deposit: 200, quantity: 2 }],
    })
  })

  it('shows Add custom product on the browse screen for an owner and hides it for a non-owner', async () => {
    seedCart([baseCartItem])
    server.use(
      http.get('*/api/items', () => ok(page([f.anItemSummary({ id: 'item-1' })]))),
    )

    setAuth('OWNER')
    const owner = renderWithProviders(<CheckoutPage />)
    await flush()
    expect(await screen.findByRole('button', { name: /add custom product/i })).toBeInTheDocument()
    owner.unmount()

    setAuth('EXECUTIVE')
    renderWithProviders(<CheckoutPage />)
    await flush()
    expect(await screen.findByRole('button', { name: 'Checkout' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /add custom product/i })).not.toBeInTheDocument()
  })

  it('lets an owner add a custom product from the Order Preview screen, without going back to browse', async () => {
    setAuth('OWNER')
    seedCart([baseCartItem])
    server.use(
      http.get('*/api/items', () => ok(page([f.anItemSummary({ id: 'item-1' })]))),
    )

    const user = userEvent.setup()
    await goToPreview()

    await user.click(await screen.findByRole('button', { name: /add custom product/i }))
    await user.type(await screen.findByLabelText(/product name/i), 'Counter Sherwani')
    await user.type(screen.getByLabelText(/total price/i), '250')
    await user.click(screen.getByRole('button', { name: 'Add to cart' }))
    await flush()

    expect(await screen.findByText('Counter Sherwani')).toBeInTheDocument()
    // baseCartItem: 300/day × 1 day × qty 1 = 300. New ad-hoc line: 250 flat × qty 1 = 250. Total 550.
    expect(screen.getByText('₹550')).toBeInTheDocument()
  })

  it('reaches Order Preview from an empty cart, with Confirm & Proceed disabled until a product is added', async () => {
    setAuth('OWNER')
    seedCart([])
    server.use(
      http.get('*/api/items', () => ok(page([]))),
    )

    const user = userEvent.setup()
    renderWithProviders(<CheckoutPage />)
    await flush()

    await user.click(await screen.findByRole('button', { name: 'Checkout' }))
    await flush()

    expect(screen.getByRole('button', { name: 'Confirm & Proceed' })).toBeDisabled()

    await user.click(await screen.findByRole('button', { name: /add custom product/i }))
    await user.type(await screen.findByLabelText(/product name/i), 'Counter Sherwani')
    await user.type(screen.getByLabelText(/total price/i), '250')
    await user.click(screen.getByRole('button', { name: 'Add to cart' }))
    await flush()

    expect(screen.getByRole('button', { name: 'Confirm & Proceed' })).toBeEnabled()
  })

  it('lets a line -- catalogue or ad-hoc -- be removed directly from Order Preview', async () => {
    setAuth('OWNER')
    const adHocItem: AdHocCartItem = {
      kind: 'ADHOC', lineKey: 'adhoc-1', itemName: 'Jwellery', size: null,
      quantity: 1, deposit: 100, flatPrice: 100,
    }
    seedCart([{ ...baseCartItem }, adHocItem])
    server.use(
      http.get('*/api/items', () => ok(page([f.anItemSummary({ id: 'item-1' })]))),
    )

    const user = userEvent.setup()
    await goToPreview()

    expect(await screen.findByText('Jwellery')).toBeInTheDocument()
    expect(screen.getByText('Royal Sherwani')).toBeInTheDocument()

    const removeButtons = screen.getAllByRole('button', { name: 'Remove' })
    expect(removeButtons).toHaveLength(2)

    await user.click(removeButtons[0]) // removes the catalogue line (Royal Sherwani, listed first)
    await flush()

    expect(screen.queryByText('Royal Sherwani')).not.toBeInTheDocument()
    expect(screen.getByText('Jwellery')).toBeInTheDocument()

    // Only the ad-hoc line remains; Confirm & Proceed stays enabled until the cart is fully empty.
    expect(screen.getByRole('button', { name: 'Confirm & Proceed' })).toBeEnabled()

    await user.click(screen.getByRole('button', { name: 'Remove' })) // removes the last remaining line
    await flush()

    expect(screen.queryByText('Jwellery')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm & Proceed' })).toBeDisabled()
  })
})

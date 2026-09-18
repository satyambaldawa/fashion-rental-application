import { describe, it, expect, afterEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { renderWithProviders, flush, screen, within } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import CheckoutPage from './CheckoutPage'
import { useAuthStore } from '../../store/authStore'
import { STORAGE_KEY as CART_STORAGE_KEY, SESSION_MARKER_KEY } from '../../hooks/useCart'
import { jwtWithRole } from '../../test/auth'
import type { Cart, CartItem, CatalogueCartItem, AdHocCartItem } from '../../types/receipt'

function setAuth(role: 'OWNER' | 'EXECUTIVE') {
  useAuthStore.setState({ token: jwtWithRole(role), role })
}

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })
const page = (content: unknown[]) => ({
  content, totalElements: content.length, totalPages: 1, number: 0, size: 20,
})

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

  it('surfaces the backend 400 when a non-owner submits a cart with an inherited ad-hoc line', async () => {
    // Accepted gap (documented on handleConfirmReceipt): the UI only blocks *creating* ad-hoc
    // lines for a non-owner, not *submitting* a cart that already has one -- e.g. inherited from
    // a shared device where the owner built one and logged out. This proves that path degrades to
    // a readable error via conflictError rather than an unhandled rejection or a silent no-op.
    setAuth('EXECUTIVE')
    const adHocItem: AdHocCartItem = {
      kind: 'ADHOC', lineKey: 'adhoc-1', itemName: 'Inherited Lehenga', size: null,
      quantity: 1, deposit: 1000, flatPrice: 500,
    }
    seedCart([adHocItem])

    server.use(
      http.get('*/api/customers/cust-1', () => ok(f.aCustomer({ id: 'cust-1' }))),
      http.post('*/api/receipts', () => HttpResponse.json(
        { success: false, data: null, error: 'Ad-hoc items can only be checked out by the owner.' },
        { status: 400 },
      )),
    )

    const user = userEvent.setup()
    renderWithProviders(<CheckoutPage />, { route: '/checkout?newCustomerId=cust-1', path: '/checkout' })
    await flush()

    await user.click(await screen.findByRole('button', { name: 'Create Receipt' }))
    await flush()

    expect(await screen.findByText('Ad-hoc items can only be checked out by the owner.')).toBeInTheDocument()
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

// Total Deposit's ₹1,000 collides with the Deposit column of the preview table, so plain
// getByText can't disambiguate — read the specific Descriptions.Item's content instead.
function descriptionValue(label: string): string {
  // The "Grand Total" label is wrapped in <strong>, so the label text lives on that
  // element rather than the .ant-descriptions-item-label span itself — match on text only.
  const labelEl = screen.getByText(label)
  const container = labelEl.closest('.ant-descriptions-item-container')!
  return container.querySelector('.ant-descriptions-item-content')!.textContent!
}

describe('CheckoutPage coupons', () => {
  afterEach(() => {
    localStorage.removeItem(CART_STORAGE_KEY)
    sessionStorage.clear()
    useAuthStore.setState({ token: null, role: null })
  })

  it('applying a valid code renders the discount row and the server totals, and the deposit is unchanged', async () => {
    seedCart([baseCartItem]) // rate 300 × 1 day × qty 1 = 300 rent, 1000 deposit
    server.use(
      http.get('*/api/items', () => ok(page([f.anItemSummary({ id: 'item-1' })]))),
      http.post('*/api/checkout/preview', async ({ request }) => {
        const body = (await request.json()) as { couponCode?: string | null }
        if (body.couponCode === 'SAVE20') {
          return ok(f.aCheckoutPreview({
            couponCode: 'SAVE20', discountAmount: 60, totalRent: 300, totalDeposit: 1000, grandTotal: 1240,
          }))
        }
        return ok(f.aCheckoutPreview())
      }),
    )

    const user = userEvent.setup()
    await goToPreview()

    await user.type(screen.getByPlaceholderText('Have a coupon?'), 'SAVE20')
    await user.click(screen.getByRole('button', { name: 'Apply' }))
    await flush()

    expect(await screen.findByText('Discount (SAVE20)')).toBeInTheDocument()
    expect(screen.getByText('−₹60')).toBeInTheDocument()
    expect(descriptionValue('Total Deposit')).toBe('₹1,000') // unchanged
    expect(descriptionValue('Grand Total')).toBe('₹1,240') // from the server
  })

  it('renders the error and leaves totals untouched when the coupon is invalid', async () => {
    seedCart([baseCartItem])
    server.use(
      http.get('*/api/items', () => ok(page([f.anItemSummary({ id: 'item-1' })]))),
      http.post('*/api/checkout/preview', () => HttpResponse.json(
        { success: false, data: null, error: "Coupon code 'BADCODE' is not valid." },
        { status: 400 },
      )),
    )

    const user = userEvent.setup()
    await goToPreview()

    await user.type(screen.getByPlaceholderText('Have a coupon?'), 'BADCODE')
    await user.click(screen.getByRole('button', { name: 'Apply' }))
    await flush()

    expect(await screen.findByText("Coupon code 'BADCODE' is not valid.")).toBeInTheDocument()
    expect(screen.queryByText(/Discount \(/)).not.toBeInTheDocument()
    // Locally computed totals (no coupon applied) are untouched.
    expect(descriptionValue('Grand Total')).toBe('₹1,300') // 300 + 1000
  })

  it('Remove clears the applied discount and reverts to the locally computed totals', async () => {
    seedCart([baseCartItem])
    server.use(
      http.get('*/api/items', () => ok(page([f.anItemSummary({ id: 'item-1' })]))),
      http.post('*/api/checkout/preview', () => ok(f.aCheckoutPreview({
        couponCode: 'SAVE20', discountAmount: 60, totalRent: 300, totalDeposit: 1000, grandTotal: 1240,
      }))),
    )

    const user = userEvent.setup()
    await goToPreview()

    await user.type(screen.getByPlaceholderText('Have a coupon?'), 'SAVE20')
    await user.click(screen.getByRole('button', { name: 'Apply' }))
    await flush()
    expect(await screen.findByText('Discount (SAVE20)')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Remove coupon' }))
    await flush()

    expect(screen.queryByText('Discount (SAVE20)')).not.toBeInTheDocument()
    expect(descriptionValue('Grand Total')).toBe('₹1,300') // back to local Grand Total
    expect(screen.getByPlaceholderText('Have a coupon?')).toBeInTheDocument()
  })

  it('clears an applied coupon when a custom product is added to the cart', async () => {
    setAuth('OWNER')
    seedCart([baseCartItem])
    server.use(
      http.get('*/api/items', () => ok(page([f.anItemSummary({ id: 'item-1' })]))),
      http.post('*/api/checkout/preview', () => ok(f.aCheckoutPreview({
        couponCode: 'SAVE20', discountAmount: 60, totalRent: 300, totalDeposit: 1000, grandTotal: 1240,
      }))),
    )

    const user = userEvent.setup()
    await goToPreview()

    await user.type(screen.getByPlaceholderText('Have a coupon?'), 'SAVE20')
    await user.click(screen.getByRole('button', { name: 'Apply' }))
    await flush()
    expect(await screen.findByText('Discount (SAVE20)')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /add custom product/i }))
    await user.type(await screen.findByLabelText(/product name/i), 'Counter Sherwani')
    await user.type(screen.getByLabelText(/total price/i), '250')
    await user.click(screen.getByRole('button', { name: 'Add to cart' }))
    await flush()

    expect(screen.queryByText('Discount (SAVE20)')).not.toBeInTheDocument()
    // The input itself must be cleared too, not just the discount row — otherwise it still
    // reads "SAVE20" as if still applied, while buildRequest would actually send null.
    expect((screen.getByPlaceholderText('Have a coupon?') as HTMLInputElement).value).toBe('')
    expect(await screen.findByText(/Coupon SAVE20 was removed because the cart changed/))
      .toBeInTheDocument()
  })

  it('discards a coupon preview response that resolves after the cart it priced has changed', async () => {
    setAuth('OWNER')
    seedCart([baseCartItem]) // rate 300 × 1 day × qty 1 = 300 rent
    server.use(http.get('*/api/items', () => ok(page([f.anItemSummary({ id: 'item-1' })]))))

    // Holds the preview response open until the test resolves it manually, simulating a
    // response that lands after the cart has already been mutated.
    let resolvePreview!: (value: Response) => void
    const pendingPreview = new Promise<Response>(resolve => { resolvePreview = resolve })
    server.use(http.post('*/api/checkout/preview', () => pendingPreview))

    const user = userEvent.setup()
    await goToPreview()

    await user.type(screen.getByPlaceholderText('Have a coupon?'), 'SAVE20')
    await user.click(screen.getByRole('button', { name: 'Apply' }))
    await flush() // request is dispatched and now in flight

    // Mutate the cart while the request is still pending — the response below was priced
    // against the qty-1 cart, not this one.
    await user.click(await screen.findByRole('button', { name: /add custom product/i }))
    await user.type(await screen.findByLabelText(/product name/i), 'Counter Sherwani')
    await user.type(screen.getByLabelText(/total price/i), '250')
    await user.click(screen.getByRole('button', { name: 'Add to cart' }))
    await flush()

    // The stale response now lands, priced for the cart as it was at Apply-time.
    resolvePreview(ok(f.aCheckoutPreview({
      couponCode: 'SAVE20', discountAmount: 60, totalRent: 300, totalDeposit: 1000, grandTotal: 1240,
    })) as unknown as Response)
    await flush()

    // Must not silently apply totals computed for a cart that no longer exists.
    expect(screen.queryByText('Discount (SAVE20)')).not.toBeInTheDocument()
    expect(await screen.findByText(
      'The cart changed while applying this coupon — please apply it again.',
    )).toBeInTheDocument()
    // 300 (Sherwani) + 250 (custom product) = 550 rent, un-discounted.
    expect(descriptionValue('Grand Total')).toBe('₹1,550') // 550 rent + 1000 deposit
  })

  function seedCartWithAppliedCoupon() {
    const cart: Cart = {
      startDatetime: '2026-04-18T10:00:00+05:30',
      endDatetime: '2026-04-19T10:00:00+05:30',
      rentalDays: 1,
      items: [baseCartItem],
      appliedCoupon: {
        couponCode: 'SAVE20', discountAmount: 60, totalRent: 300, totalDeposit: 1000, grandTotal: 1240,
      },
    }
    localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(cart))
  }

  it('preserves an applied coupon across the New Customer round trip within the same browser tab session', async () => {
    // Registering a new customer mid-checkout (the "New Customer" button) is a full route
    // change to a separate page, not a screen within CheckoutPage — the component actually
    // unmounts, so a fresh useCart() instance re-reads localStorage on the way back. Found
    // via manual testing: before this fix, the coupon silently vanished on this exact trip
    // with zero indication, seconds after being applied — the single most common mid-checkout
    // detour. sessionStorage (not cleared by an internal route change) is what now tells
    // useCart's loadCart() this is the same session, not a reopened tab: set the marker here
    // exactly as an earlier mount of CheckoutPage this session already would have.
    sessionStorage.setItem(SESSION_MARKER_KEY, '1')
    seedCartWithAppliedCoupon()
    server.use(
      http.get('*/api/items', () => ok(page([f.anItemSummary({ id: 'item-1' })]))),
      http.get('*/api/customers/cust-1', () => ok(f.aCustomer({ id: 'cust-1' }))),
    )

    renderWithProviders(<CheckoutPage />, { route: '/checkout?newCustomerId=cust-1', path: '/checkout' })
    await flush()

    expect(screen.queryByText(/was not carried over/)).not.toBeInTheDocument()
    expect(await screen.findByText('Discount (SAVE20)')).toBeInTheDocument()
    expect(descriptionValue('Grand Total')).toBe('₹1,240')
  })

  it('warns once on mount when a coupon does not survive because the browser tab was actually closed and reopened', async () => {
    // The genuine cross-session case: sessionStorage cleared (simulating the tab having been
    // closed), so loadCart() correctly still treats the coupon as potentially stale.
    sessionStorage.clear()
    seedCartWithAppliedCoupon()
    server.use(
      http.get('*/api/items', () => ok(page([f.anItemSummary({ id: 'item-1' })]))),
      http.get('*/api/customers/cust-1', () => ok(f.aCustomer({ id: 'cust-1' }))),
    )

    renderWithProviders(<CheckoutPage />, { route: '/checkout?newCustomerId=cust-1', path: '/checkout' })
    await flush()

    expect(await screen.findByText(/Coupon SAVE20 was not carried over/)).toBeInTheDocument()
    expect(screen.queryByText('Discount (SAVE20)')).not.toBeInTheDocument()
    expect(descriptionValue('Grand Total')).toBe('₹1,300') // 300 + 1000, un-discounted
  })
})

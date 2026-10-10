import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { renderWithProviders, flush, screen } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import { useAuthStore } from '../../store/authStore'
import { STORAGE_KEY as CART_STORAGE_KEY, SESSION_MARKER_KEY } from '../../hooks/useCart'
import type { Cart, CatalogueCartItem } from '../../types/receipt'
import ReceiptDetailPage from './ReceiptDetailPage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })

beforeEach(() => useAuthStore.setState({ token: 'test-token', role: 'OWNER' }))

async function renderReceipt() {
  renderWithProviders(<ReceiptDetailPage />, { route: '/receipts/rcpt-1', path: '/receipts/:id' })
  await flush()
}

// renderWithProviders only mounts a single route — "Add items" tests need a real
// navigation target to assert against, so this mounts a stub at /checkout alongside it.
function renderReceiptWithCheckoutStub() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ConfigProvider>
        <MemoryRouter initialEntries={['/receipts/rcpt-1']}>
          <Routes>
            <Route path="/receipts/:id" element={<ReceiptDetailPage />} />
            <Route path="/checkout" element={<div data-testid="checkout-stub" />} />
          </Routes>
        </MemoryRouter>
      </ConfigProvider>
    </QueryClientProvider>,
  )
}

const aCartItem: CatalogueCartItem = {
  kind: 'CATALOGUE', lineKey: 'item-1', itemId: 'item-1', itemName: 'Royal Sherwani',
  itemType: 'INDIVIDUAL', category: 'COSTUME', size: 'M', componentNames: null,
  thumbnailUrl: null, rate: 300, deposit: 1000, quantity: 1, availableQuantity: 3,
}

describe('ReceiptDetailPage discount row', () => {
  it('renders the discount row when the receipt has a coupon applied', async () => {
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ couponCode: 'SAVE20', discountAmount: 60, totalRent: 600 }))))

    await renderReceipt()

    expect(await screen.findByText('Discount (SAVE20)')).toBeInTheDocument()
    expect(screen.getByText('−₹60')).toBeInTheDocument()
  })

  it('omits the discount row when the receipt has no coupon', async () => {
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ couponCode: null, discountAmount: 0 }))))

    await renderReceipt()

    expect(await screen.findByText('Total Rent')).toBeInTheDocument()
    expect(screen.queryByText(/Discount \(/)).not.toBeInTheDocument()
  })
})

describe('ReceiptDetailPage WhatsApp share', () => {
  it('includes a link to the review page in the WhatsApp message', async () => {
    server.use(http.get('*/api/receipts/rcpt-1', () => ok(f.aReceipt())))

    await renderReceipt()

    const link = await screen.findByRole('link', { name: /Send on WhatsApp/i })
    const decodedHref = decodeURIComponent(link.getAttribute('href') ?? '')

    expect(decodedHref).toContain(`${window.location.origin}/review`)
  })
})

describe('ReceiptDetailPage "Add items" (#165)', () => {
  afterEach(() => {
    localStorage.removeItem(CART_STORAGE_KEY)
    sessionStorage.clear()
    vi.useRealTimers()
  })

  // Fixture dates are 2026-04-18 (start) → 2026-04-19 (end). Faking only Date keeps
  // msw/React Query's own timers (and flush()'s setTimeout) running normally.
  function setNow(iso: string) {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(iso))
  }

  it('AC1: shows Add items for a GIVEN receipt whose end is still in the future', async () => {
    setNow('2026-04-18T12:00:00+05:30')
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ status: 'GIVEN', endDatetime: '2026-04-19T10:00:00+05:30' }))))

    await renderReceipt()

    expect(await screen.findByRole('button', { name: /Add items/i })).toBeInTheDocument()
  })

  it('AC2: hides Add items once the GIVEN receipt is overdue', async () => {
    setNow('2026-04-20T00:00:00+05:30')
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ status: 'GIVEN', endDatetime: '2026-04-19T10:00:00+05:30' }))))

    await renderReceipt()

    expect(await screen.findByText('R-2026-0001')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Add items/i })).not.toBeInTheDocument()
  })

  it('AC2: hides Add items for a RETURNED receipt even with a future end', async () => {
    setNow('2026-04-18T12:00:00+05:30')
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ status: 'RETURNED', endDatetime: '2026-04-19T10:00:00+05:30' }))))

    await renderReceipt()

    expect(await screen.findByText('R-2026-0001')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Add items/i })).not.toBeInTheDocument()
  })

  it('AC3/AC11: seeds a fresh cart from the receipt and navigates to /checkout, dropping the coupon and notes', async () => {
    setNow('2026-04-18T12:00:00+05:30')
    server.use(http.get('*/api/receipts/rcpt-1', () => ok(f.aReceipt({
      status: 'GIVEN',
      customerId: 'cust-9', customerName: 'Priya', customerPhone: '9900011122',
      startDatetime: '2026-04-18T10:00:00+05:30', endDatetime: '2026-04-19T10:00:00+05:30',
      rentalDays: 1, couponCode: 'SAVE20', discountAmount: 60, notes: 'Handle with care',
    }))))

    const user = userEvent.setup()
    renderReceiptWithCheckoutStub()
    await flush()

    await user.click(await screen.findByRole('button', { name: /Add items/i }))
    await flush()

    expect(await screen.findByTestId('checkout-stub')).toBeInTheDocument()
    expect(JSON.parse(localStorage.getItem(CART_STORAGE_KEY)!)).toEqual({
      startDatetime: '2026-04-18T10:00:00+05:30',
      endDatetime: '2026-04-19T10:00:00+05:30',
      rentalDays: 1,
      items: [],
      appliedCoupon: null,
      customer: { id: 'cust-9', name: 'Priya', phone: '9900011122' },
    })
  })

  it('AC5: silently replaces a stored cart that has no items, with no confirm dialog', async () => {
    setNow('2026-04-18T12:00:00+05:30')
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ status: 'GIVEN', endDatetime: '2026-04-19T10:00:00+05:30' }))))
    const emptyCart: Cart = { startDatetime: 'x', endDatetime: 'y', rentalDays: 2, items: [] }
    localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(emptyCart))

    const user = userEvent.setup()
    renderReceiptWithCheckoutStub()
    await flush()

    await user.click(await screen.findByRole('button', { name: /Add items/i }))
    await flush()

    expect(screen.queryByText('Discard the cart in progress?')).not.toBeInTheDocument()
    expect(await screen.findByTestId('checkout-stub')).toBeInTheDocument()
  })

  it('AC4: a non-empty cart in progress prompts to discard, and Cancel leaves it untouched', async () => {
    setNow('2026-04-18T12:00:00+05:30')
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ status: 'GIVEN', endDatetime: '2026-04-19T10:00:00+05:30' }))))
    const inProgressCart: Cart = { startDatetime: 'x', endDatetime: 'y', rentalDays: 2, items: [aCartItem] }
    const storedJson = JSON.stringify(inProgressCart)
    localStorage.setItem(CART_STORAGE_KEY, storedJson)

    const user = userEvent.setup()
    renderReceiptWithCheckoutStub()
    await flush()

    await user.click(await screen.findByRole('button', { name: /Add items/i }))

    expect(await screen.findAllByText('Discard the cart in progress?')).not.toHaveLength(0)

    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    await flush()

    expect(localStorage.getItem(CART_STORAGE_KEY)).toBe(storedJson)
    expect(screen.queryByTestId('checkout-stub')).not.toBeInTheDocument()
  })

  it('AC4: OK discards the cart in progress and starts the new customer cart', async () => {
    setNow('2026-04-18T12:00:00+05:30')
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ status: 'GIVEN', customerId: 'cust-9', customerName: 'Priya', customerPhone: '9900011122' }))))
    const inProgressCart: Cart = { startDatetime: 'x', endDatetime: 'y', rentalDays: 2, items: [aCartItem] }
    localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(inProgressCart))

    const user = userEvent.setup()
    renderReceiptWithCheckoutStub()
    await flush()

    await user.click(await screen.findByRole('button', { name: /Add items/i }))
    await screen.findAllByText('Discard the cart in progress?')

    await user.click(screen.getByRole('button', { name: 'Discard and continue' }))
    await flush()

    expect(await screen.findByTestId('checkout-stub')).toBeInTheDocument()
    const stored = JSON.parse(localStorage.getItem(CART_STORAGE_KEY)!)
    expect(stored.items).toEqual([])
    expect(stored.customer).toEqual({ id: 'cust-9', name: 'Priya', phone: '9900011122' })
  })

  it('never writes the session marker while seeding a cart from a receipt', async () => {
    setNow('2026-04-18T12:00:00+05:30')
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ status: 'GIVEN', endDatetime: '2026-04-19T10:00:00+05:30' }))))

    const user = userEvent.setup()
    renderReceiptWithCheckoutStub()
    await flush()

    // Checked before the click too — a real CheckoutPage mount at the stub route would
    // legitimately write this marker, so this only proves ReceiptDetailPage itself never does.
    expect(sessionStorage.getItem(SESSION_MARKER_KEY)).toBeNull()

    await user.click(await screen.findByRole('button', { name: /Add items/i }))
    await flush()

    expect(sessionStorage.getItem(SESSION_MARKER_KEY)).toBeNull()
  })

  it('click-time recheck: warns and writes no cart when the rental became overdue after the button rendered', async () => {
    setNow('2026-04-18T12:00:00+05:30')
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ status: 'GIVEN', endDatetime: '2026-04-19T10:00:00+05:30' }))))

    const user = userEvent.setup()
    renderReceiptWithCheckoutStub()
    await flush()

    const button = await screen.findByRole('button', { name: /Add items/i })
    vi.setSystemTime(new Date('2026-04-20T00:00:00+05:30')) // now overdue

    await user.click(button)
    await flush()

    expect(await screen.findByText('This rental is now overdue — items can no longer be added.')).toBeInTheDocument()
    expect(localStorage.getItem(CART_STORAGE_KEY)).toBeNull()
    expect(screen.queryByTestId('checkout-stub')).not.toBeInTheDocument()
  })
})

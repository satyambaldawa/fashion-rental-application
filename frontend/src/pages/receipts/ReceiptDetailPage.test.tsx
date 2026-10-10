import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import dayjs from 'dayjs'
import { renderWithProviders, flush, screen, within } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import { useAuthStore } from '../../store/authStore'
import { jwtWithRole } from '../../test/auth'
import { STORAGE_KEY as CART_STORAGE_KEY, SESSION_MARKER_KEY } from '../../hooks/useCart'
import type { Cart } from '../../types/receipt'
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
            <Route path="/checkout" element={<h1>Checkout stub</h1>} />
          </Routes>
        </MemoryRouter>
      </ConfigProvider>
    </QueryClientProvider>,
  )
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

    expect(await screen.findByRole('heading', { name: 'Checkout stub' })).toBeInTheDocument()
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
    expect(await screen.findByRole('heading', { name: 'Checkout stub' })).toBeInTheDocument()
  })

  it('AC4: a non-empty cart in progress prompts to discard, and Cancel leaves it untouched', async () => {
    setNow('2026-04-18T12:00:00+05:30')
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ status: 'GIVEN', endDatetime: '2026-04-19T10:00:00+05:30' }))))
    const inProgressCart: Cart = { startDatetime: 'x', endDatetime: 'y', rentalDays: 2, items: [f.aCatalogueCartItem()] }
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
    expect(screen.queryByRole('heading', { name: 'Checkout stub' })).not.toBeInTheDocument()
  })

  it('AC4: OK discards the cart in progress and starts the new customer cart', async () => {
    setNow('2026-04-18T12:00:00+05:30')
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ status: 'GIVEN', customerId: 'cust-9', customerName: 'Priya', customerPhone: '9900011122' }))))
    const inProgressCart: Cart = { startDatetime: 'x', endDatetime: 'y', rentalDays: 2, items: [f.aCatalogueCartItem()] }
    localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(inProgressCart))

    const user = userEvent.setup()
    renderReceiptWithCheckoutStub()
    await flush()

    await user.click(await screen.findByRole('button', { name: /Add items/i }))
    await screen.findAllByText('Discard the cart in progress?')

    await user.click(screen.getByRole('button', { name: 'Discard and continue' }))
    await flush()

    expect(await screen.findByRole('heading', { name: 'Checkout stub' })).toBeInTheDocument()
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
    expect(screen.queryByRole('heading', { name: 'Checkout stub' })).not.toBeInTheDocument()
  })
})

describe('ReceiptDetailPage cancel receipt flow (#166)', () => {
  function asOwner() {
    useAuthStore.setState({ token: jwtWithRole('OWNER'), role: 'OWNER' })
  }
  function asExecutive() {
    useAuthStore.setState({ token: jwtWithRole('EXECUTIVE'), role: 'EXECUTIVE' })
  }

  // Far enough in the future to always be well clear of the 12-hour cutoff, in CI or locally.
  const FAR_FUTURE = dayjs().add(13, 'hour').toISOString()

  // The reason modal's OK button is also labelled "Cancel receipt", same as the page's
  // trigger button underneath it — the modal's copy (rendered into a portal) is the last
  // one in the DOM.
  function reasonModalSubmitButton() {
    const buttons = screen.getAllByRole('button', { name: 'Cancel receipt' })
    return buttons[buttons.length - 1]
  }

  it('shows the Cancel receipt button for an OWNER viewing an eligible GIVEN receipt', async () => {
    asOwner()
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ status: 'GIVEN', endDatetime: FAR_FUTURE }))))

    await renderReceipt()

    expect(await screen.findByRole('button', { name: 'Cancel receipt' })).toBeInTheDocument()
  })

  it('hides the Cancel receipt button for an EXECUTIVE', async () => {
    asExecutive()
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ status: 'GIVEN', endDatetime: FAR_FUTURE }))))

    await renderReceipt()

    expect(await screen.findByText('R-2026-0001')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel receipt' })).not.toBeInTheDocument()
  })

  it('hides the Cancel receipt button for a RETURNED receipt', async () => {
    asOwner()
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ status: 'RETURNED', endDatetime: FAR_FUTURE }))))

    await renderReceipt()

    expect(await screen.findByText('R-2026-0001')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel receipt' })).not.toBeInTheDocument()
  })

  it('hides the Cancel receipt button for an already-CANCELLED receipt', async () => {
    asOwner()
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ status: 'CANCELLED', endDatetime: FAR_FUTURE, cancellation: f.aReceiptCancellation() }))))

    await renderReceipt()

    expect(await screen.findByText('R-2026-0001')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel receipt' })).not.toBeInTheDocument()
  })

  it('hides the Cancel receipt button for an overdue GIVEN receipt', async () => {
    asOwner()
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ status: 'GIVEN', endDatetime: dayjs().subtract(1, 'hour').toISOString() }))))

    await renderReceipt()

    expect(await screen.findByText('R-2026-0001')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel receipt' })).not.toBeInTheDocument()
  })

  it('hides the Cancel receipt button when the receipt ends within 12 hours', async () => {
    asOwner()
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ status: 'GIVEN', endDatetime: dayjs().add(11, 'hour').toISOString() }))))

    await renderReceipt()

    expect(await screen.findByText('R-2026-0001')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel receipt' })).not.toBeInTheDocument()
  })

  it('step 1: opens the confirm dialog with the refunded amount, and No sends no request', async () => {
    asOwner()
    let cancelCalled = false
    server.use(
      http.get('*/api/receipts/rcpt-1', () => ok(f.aReceipt({ status: 'GIVEN', endDatetime: FAR_FUTURE, grandTotal: 1300 }))),
      http.post('*/api/receipts/rcpt-1/cancel', () => { cancelCalled = true; return ok(f.aReceipt()) }),
    )

    const user = userEvent.setup()
    await renderReceipt()

    await user.click(await screen.findByRole('button', { name: 'Cancel receipt' }))

    const confirmTitle = await screen.findByText('Cancel this receipt?')
    const confirmModal = confirmTitle.closest('.ant-modal-content') as HTMLElement
    expect(within(confirmModal).getByText(/Do you really want to cancel receipt/)).toBeInTheDocument()
    expect(within(confirmModal).getByText(/₹1,300/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'No' }))
    await flush()

    expect(screen.queryByText('Cancel this receipt?')).not.toBeInTheDocument()
    expect(screen.queryByText('Why is this receipt being cancelled?')).not.toBeInTheDocument()
    expect(cancelCalled).toBe(false)
  })

  it('step 2: Yes opens the reason dialog with four radio options', async () => {
    asOwner()
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ status: 'GIVEN', endDatetime: FAR_FUTURE }))))

    const user = userEvent.setup()
    await renderReceipt()

    await user.click(await screen.findByRole('button', { name: 'Cancel receipt' }))
    await user.click(screen.getByRole('button', { name: 'Yes, money returned. Continue' }))

    expect(await screen.findByText('Why is this receipt being cancelled?')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'Wrong order' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: "Customer doesn't want it" })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'Change order dates' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'Other' })).toBeInTheDocument()
  })

  it('Close on the reason dialog sends no request', async () => {
    asOwner()
    let cancelCalled = false
    server.use(
      http.get('*/api/receipts/rcpt-1', () => ok(f.aReceipt({ status: 'GIVEN', endDatetime: FAR_FUTURE }))),
      http.post('*/api/receipts/rcpt-1/cancel', () => { cancelCalled = true; return ok(f.aReceipt()) }),
    )

    const user = userEvent.setup()
    await renderReceipt()

    await user.click(await screen.findByRole('button', { name: 'Cancel receipt' }))
    await user.click(screen.getByRole('button', { name: 'Yes, money returned. Continue' }))
    const reasonTitle = await screen.findByText('Why is this receipt being cancelled?')
    const reasonModal = reasonTitle.closest('.ant-modal-content') as HTMLElement
    const footer = reasonModal.querySelector('.ant-modal-footer') as HTMLElement
    await user.click(within(footer).getByRole('button', { name: 'Close' }))
    await flush()

    expect(screen.queryByText('Why is this receipt being cancelled?')).not.toBeInTheDocument()
    expect(cancelCalled).toBe(false)
  })

  it('choosing Other and submitting with no text shows a validation error and sends no request', async () => {
    asOwner()
    let cancelCalled = false
    server.use(
      http.get('*/api/receipts/rcpt-1', () => ok(f.aReceipt({ status: 'GIVEN', endDatetime: FAR_FUTURE }))),
      http.post('*/api/receipts/rcpt-1/cancel', () => { cancelCalled = true; return ok(f.aReceipt()) }),
    )

    const user = userEvent.setup()
    await renderReceipt()

    await user.click(await screen.findByRole('button', { name: 'Cancel receipt' }))
    await user.click(screen.getByRole('button', { name: 'Yes, money returned. Continue' }))
    await screen.findByText('Why is this receipt being cancelled?')

    await user.click(screen.getByRole('radio', { name: 'Other' }))
    await user.click(reasonModalSubmitButton())

    expect(await screen.findByText('Please describe the reason')).toBeInTheDocument()
    expect(cancelCalled).toBe(false)
  })

  it('choosing Other and submitting whitespace-only text shows a validation error and sends no request', async () => {
    asOwner()
    let cancelCalled = false
    server.use(
      http.get('*/api/receipts/rcpt-1', () => ok(f.aReceipt({ status: 'GIVEN', endDatetime: FAR_FUTURE }))),
      http.post('*/api/receipts/rcpt-1/cancel', () => { cancelCalled = true; return ok(f.aReceipt()) }),
    )

    const user = userEvent.setup()
    await renderReceipt()

    await user.click(await screen.findByRole('button', { name: 'Cancel receipt' }))
    await user.click(screen.getByRole('button', { name: 'Yes, money returned. Continue' }))
    await screen.findByText('Why is this receipt being cancelled?')

    await user.click(screen.getByRole('radio', { name: 'Other' }))
    await user.type(screen.getByPlaceholderText('Describe the reason'), '   ')
    await user.click(reasonModalSubmitButton())

    expect(await screen.findByText('Please describe the reason')).toBeInTheDocument()
    expect(cancelCalled).toBe(false)
  })

  it('submits WRONG_ORDER and shows the cancelled status and cancellation details', async () => {
    asOwner()
    let capturedBody: unknown = null
    server.use(
      http.get('*/api/receipts/rcpt-1', () => ok(f.aReceipt({ status: 'GIVEN', endDatetime: FAR_FUTURE }))),
      http.post('*/api/receipts/rcpt-1/cancel', async ({ request }) => {
        capturedBody = await request.json()
        return ok(f.aReceipt({
          status: 'CANCELLED',
          cancellation: f.aReceiptCancellation({ reason: 'WRONG_ORDER', reasonDetail: null }),
        }))
      }),
    )

    const user = userEvent.setup()
    await renderReceipt()

    await user.click(await screen.findByRole('button', { name: 'Cancel receipt' }))
    await user.click(screen.getByRole('button', { name: 'Yes, money returned. Continue' }))
    await screen.findByText('Why is this receipt being cancelled?')

    await user.click(screen.getByRole('radio', { name: 'Wrong order' }))
    await user.click(reasonModalSubmitButton())
    await flush()

    expect(capturedBody).toEqual({ reason: 'WRONG_ORDER', reasonDetail: null })
    expect(await screen.findByText('This receipt was cancelled')).toBeInTheDocument()
    expect(screen.getByText('Wrong order')).toBeInTheDocument()
  })

  it('submits OTHER with detail text', async () => {
    asOwner()
    let capturedBody: unknown = null
    server.use(
      http.get('*/api/receipts/rcpt-1', () => ok(f.aReceipt({ status: 'GIVEN', endDatetime: FAR_FUTURE }))),
      http.post('*/api/receipts/rcpt-1/cancel', async ({ request }) => {
        capturedBody = await request.json()
        return ok(f.aReceipt({
          status: 'CANCELLED',
          cancellation: f.aReceiptCancellation({ reason: 'OTHER', reasonDetail: 'customer moved cities' }),
        }))
      }),
    )

    const user = userEvent.setup()
    await renderReceipt()

    await user.click(await screen.findByRole('button', { name: 'Cancel receipt' }))
    await user.click(screen.getByRole('button', { name: 'Yes, money returned. Continue' }))
    await screen.findByText('Why is this receipt being cancelled?')

    await user.click(screen.getByRole('radio', { name: 'Other' }))
    await user.type(screen.getByPlaceholderText('Describe the reason'), 'customer moved cities')
    await user.click(reasonModalSubmitButton())
    await flush()

    expect(capturedBody).toEqual({ reason: 'OTHER', reasonDetail: 'customer moved cities' })
    expect(await screen.findByText('customer moved cities')).toBeInTheDocument()
  })

  it('shows the server error message when the server rejects the cancellation', async () => {
    asOwner()
    server.use(
      http.get('*/api/receipts/rcpt-1', () => ok(f.aReceipt({ status: 'GIVEN', endDatetime: FAR_FUTURE }))),
      http.post('*/api/receipts/rcpt-1/cancel', () =>
        HttpResponse.json(
          { success: false, data: null, error: 'Receipt R-2026-0001 has already been cancelled' },
          { status: 409 },
        )),
    )

    const user = userEvent.setup()
    await renderReceipt()

    await user.click(await screen.findByRole('button', { name: 'Cancel receipt' }))
    await user.click(screen.getByRole('button', { name: 'Yes, money returned. Continue' }))
    await screen.findByText('Why is this receipt being cancelled?')

    await user.click(screen.getByRole('radio', { name: 'Wrong order' }))
    await user.click(reasonModalSubmitButton())

    expect(await screen.findByText('Receipt R-2026-0001 has already been cancelled')).toBeInTheDocument()
    // The reason dialog stays open on error.
    expect(screen.getByText('Why is this receipt being cancelled?')).toBeInTheDocument()
  })

  it('shows the reason label, formatted time and cancelled-by username for an already-cancelled receipt', async () => {
    asOwner()
    const cancelledAt = '2026-04-18T12:00:00+05:30'
    server.use(http.get('*/api/receipts/rcpt-1', () => ok(f.aReceipt({
      status: 'CANCELLED',
      cancellation: f.aReceiptCancellation({
        cancelledAt, cancelledByUsername: 'owner', reason: 'CHANGE_ORDER_DATES',
      }),
    }))))

    await renderReceipt()

    expect(await screen.findByText('This receipt was cancelled')).toBeInTheDocument()
    expect(screen.getByText(dayjs(cancelledAt).format('DD MMM YYYY, h:mm A'))).toBeInTheDocument()
    expect(screen.getByText('owner')).toBeInTheDocument()
    expect(screen.getByText('Change order dates')).toBeInTheDocument()
  })
})

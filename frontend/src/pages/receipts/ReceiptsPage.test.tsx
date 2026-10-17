import { describe, it, expect, beforeEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import { MemoryRouter, Route, Routes, useParams } from 'react-router-dom'
import dayjs from 'dayjs'
import { flush, screen, waitFor, within } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import { useAuthStore } from '../../store/authStore'
import { jwtWithRole } from '../../test/auth'
import ReceiptsPage from './ReceiptsPage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })
const failure = () => HttpResponse.json({ success: false, data: null, error: 'boom' }, { status: 500 })

// Picked up today, so the card sits in the "Today" pickup-date group that is open by default.
const aSummary = (o: Parameters<typeof f.aReceiptSummary>[0] = {}) =>
  f.aReceiptSummary({ startDatetime: dayjs().toISOString(), ...o })

beforeEach(() => useAuthStore.setState({ token: jwtWithRole('OWNER'), role: 'OWNER' }))

function renderReceiptsPage(detailElement: React.ReactNode = <h1>Receipt detail stub</h1>) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ConfigProvider>
        <MemoryRouter initialEntries={['/receipts']}>
          <Routes>
            <Route path="/receipts" element={<ReceiptsPage />} />
            <Route path="/receipts/:id" element={detailElement} />
          </Routes>
        </MemoryRouter>
      </ConfigProvider>
    </QueryClientProvider>,
  )
}

function ReceiptIdStub() {
  const { id } = useParams()
  return <h1>Receipt detail: {id}</h1>
}

describe('ReceiptsPage Active Rentals card amounts (#168)', () => {
  it('shows Rent, Deposit and Total separately when there is no coupon', async () => {
    server.use(http.get('*/api/receipts', () => ok([aSummary({
      totalRent: 300, totalDeposit: 1000, grandTotal: 1300, couponCode: null, discountAmount: 0,
    })])))

    renderReceiptsPage()
    await screen.findByText('Rent')

    expect(within(screen.getByText('Rent').parentElement!).getByText('₹300')).toBeInTheDocument()
    expect(within(screen.getByText('Deposit').parentElement!).getByText('₹1,000')).toBeInTheDocument()
    expect(within(screen.getByText('Total').parentElement!).getByText('₹1,300')).toBeInTheDocument()
    expect(screen.queryByText(/SAVE/)).not.toBeInTheDocument()
  })

  it('shows the pre-discount Rent, the coupon tag and the backend grandTotal when a coupon is applied', async () => {
    server.use(http.get('*/api/receipts', () => ok([aSummary({
      totalRent: 1000, discountAmount: 100, couponCode: 'SAVE100', totalDeposit: 500, grandTotal: 1400,
    })])))

    renderReceiptsPage()
    await screen.findByText('Rent')

    expect(within(screen.getByText('Rent').parentElement!).getByText('₹1,000')).toBeInTheDocument()
    expect(within(screen.getByText('Deposit').parentElement!).getByText('₹500')).toBeInTheDocument()
    expect(within(screen.getByText('Total').parentElement!).getByText('₹1,400')).toBeInTheDocument()
    expect(screen.getByText('SAVE100 −₹100')).toBeInTheDocument()
  })

  it('shows grandTotal exactly as returned, even when it is not Rent + Deposit', async () => {
    server.use(http.get('*/api/receipts', () => ok([aSummary({
      totalRent: 300, totalDeposit: 1000, grandTotal: 1250, couponCode: null, discountAmount: 0,
    })])))

    renderReceiptsPage()
    await screen.findByText('Rent')

    expect(within(screen.getByText('Total').parentElement!).getByText('₹1,250')).toBeInTheDocument()
    expect(screen.queryByText('₹1,300')).not.toBeInTheDocument()
  })

  it('shows correct labelled amounts on the Overdue tab', async () => {
    server.use(http.get('*/api/receipts', () => ok([aSummary({
      id: 'rcpt-overdue', isOverdue: true, overdueHours: 5,
      totalRent: 450, totalDeposit: 800, grandTotal: 1250, couponCode: null, discountAmount: 0,
    })])))

    const user = userEvent.setup()
    renderReceiptsPage()
    await screen.findByText('Rent')

    await user.click(screen.getByRole('tab', { name: /overdue/i }))

    const panel = within(screen.getByRole('tabpanel'))
    expect(within(panel.getByText('Rent').parentElement!).getByText('₹450')).toBeInTheDocument()
    expect(within(panel.getByText('Deposit').parentElement!).getByText('₹800')).toBeInTheDocument()
    expect(within(panel.getByText('Total').parentElement!).getByText('₹1,250')).toBeInTheDocument()
  })

  it('replaces Process Return with a View button', async () => {
    server.use(http.get('*/api/receipts', () => ok([aSummary()])))

    renderReceiptsPage()
    await screen.findByText('Rent')

    expect(screen.queryByRole('button', { name: /process return/i })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /view/i })).toBeInTheDocument()
  })

  it('navigates to the receipt detail page when View is clicked', async () => {
    server.use(http.get('*/api/receipts', () => ok([aSummary({ id: 'rcpt-42' })])))

    const user = userEvent.setup()
    renderReceiptsPage()
    await screen.findByText('Rent')

    await user.click(screen.getByRole('button', { name: /view/i }))

    expect(await screen.findByRole('heading', { name: 'Receipt detail stub' })).toBeInTheDocument()
  })

  it('navigates to that specific receipt\'s id, not just any detail route', async () => {
    server.use(http.get('*/api/receipts', () => ok([aSummary({ id: 'rcpt-99' })])))

    const user = userEvent.setup()
    renderReceiptsPage(<ReceiptIdStub />)

    await user.click(await screen.findByRole('button', { name: /view/i }))

    expect(await screen.findByRole('heading', { name: 'Receipt detail: rcpt-99' })).toBeInTheDocument()
  })

  it('gives the View button a 44px tap target', async () => {
    server.use(http.get('*/api/receipts', () => ok([aSummary()])))

    renderReceiptsPage()
    await screen.findByText('Rent')

    expect(screen.getByRole('button', { name: /view/i })).toHaveStyle({ minHeight: '44px' })
  })
})

function stubReceiptLists({ active = [aSummary()], cancelled = [] as unknown[] } = {}) {
  server.use(http.get('*/api/receipts', ({ request }) => {
    const status = new URL(request.url).searchParams.get('status')
    return ok(status === 'CANCELLED' ? cancelled : active)
  }))
}

async function renderAndOpenCancelledTab() {
  renderReceiptsPage()
  await userEvent.setup().click(await screen.findByRole('tab', { name: /Cancelled \(last 7 days\)/ }))
}

describe('ReceiptsPage cancelled tab (#166)', () => {
  it('lists recently cancelled receipts with a Cancelled tag and a View button', async () => {
    stubReceiptLists({
      cancelled: [aSummary({ id: 'rcpt-9', receiptNumber: 'R-2026-0009', status: 'CANCELLED' })],
    })

    await renderAndOpenCancelledTab()

    const panel = within(screen.getByRole('tabpanel'))
    expect(await panel.findByText('R-2026-0009')).toBeInTheDocument()
    expect(panel.getByText('Cancelled')).toBeInTheDocument()
    expect(panel.getByRole('button', { name: /view/i })).toBeInTheDocument()
    expect(panel.queryByRole('button', { name: 'Cancel receipt' })).not.toBeInTheDocument()
  })

  it('shows an empty state when nothing was cancelled in the last 7 days', async () => {
    stubReceiptLists({ cancelled: [] })

    await renderAndOpenCancelledTab()

    expect(await screen.findByText('No receipts cancelled in the last 7 days')).toBeInTheDocument()
  })

  it('shows an error instead of an empty list when the cancelled receipts fail to load', async () => {
    server.use(http.get('*/api/receipts', ({ request }) =>
      new URL(request.url).searchParams.get('status') === 'CANCELLED' ? failure() : ok([aSummary()])))

    await renderAndOpenCancelledTab()

    expect(await screen.findByText('Failed to load cancelled receipts. Please try again.')).toBeInTheDocument()
    expect(screen.queryByText('No receipts cancelled in the last 7 days')).not.toBeInTheDocument()
  })
})

describe('ReceiptsPage cancel from the Active Rentals card', () => {
  // Well clear of the 12-hour cutoff whenever the suite runs.
  const ELIGIBLE_END = dayjs().add(2, 'day').toISOString()

  it('offers a red bin Cancel receipt button, as tall as View, to an OWNER on an eligible card', async () => {
    stubReceiptLists({ active: [aSummary({ status: 'GENERATED', endDatetime: ELIGIBLE_END })] })

    renderReceiptsPage()
    await flush()

    expect(await screen.findByRole('button', { name: 'Cancel receipt' })).toHaveStyle({ height: '44px' })
  })

  it('hides the Cancel receipt button from an EXECUTIVE', async () => {
    useAuthStore.setState({ token: jwtWithRole('EXECUTIVE'), role: 'EXECUTIVE' })
    stubReceiptLists({ active: [aSummary({ status: 'GENERATED', endDatetime: ELIGIBLE_END })] })

    renderReceiptsPage()
    await flush()

    expect(await screen.findByRole('button', { name: /view/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel receipt' })).not.toBeInTheDocument()
  })

  it('hides the Cancel receipt button once the dress has been given to the customer', async () => {
    stubReceiptLists({ active: [aSummary({ status: 'GIVEN', endDatetime: ELIGIBLE_END })] })

    renderReceiptsPage()
    await flush()

    expect(await screen.findByRole('button', { name: /view/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel receipt' })).not.toBeInTheDocument()
  })

  it('still offers Cancel receipt on a booking that ends within 12 hours, because the dress never left the shop', async () => {
    stubReceiptLists({ active: [aSummary({ status: 'GENERATED', endDatetime: dayjs().add(1, 'hour').toISOString() })] })

    renderReceiptsPage()
    await flush()

    expect(await screen.findByRole('button', { name: 'Cancel receipt' })).toBeInTheDocument()
  })

  it('cancels from the card and moves the receipt to the Cancelled tab', async () => {
    const summary = aSummary({ id: 'rcpt-7', receiptNumber: 'R-2026-0007', status: 'GENERATED', endDatetime: ELIGIBLE_END })
    let isCancelled = false
    server.use(
      http.get('*/api/receipts', ({ request }) => {
        const wantsCancelled = new URL(request.url).searchParams.get('status') === 'CANCELLED'
        if (wantsCancelled) return ok(isCancelled ? [{ ...summary, status: 'CANCELLED' }] : [])
        return ok(isCancelled ? [] : [summary])
      }),
      http.post('*/api/receipts/rcpt-7/cancel', () => {
        isCancelled = true
        return ok(f.aReceipt({ id: 'rcpt-7', status: 'CANCELLED', cancellation: f.aReceiptCancellation() }))
      }),
    )
    const user = userEvent.setup()
    renderReceiptsPage()
    await flush()

    await user.click(await screen.findByRole('button', { name: 'Cancel receipt' }))
    await user.click(await screen.findByRole('button', { name: 'Yes, money returned. Continue' }))
    await user.click(await screen.findByRole('radio', { name: 'Wrong order' }))
    await user.click(await screen.findByRole('button', { name: 'Confirm cancellation' }))
    await flush()

    expect(await screen.findByText('No active rentals')).toBeInTheDocument()
    await user.click(screen.getByRole('tab', { name: /Cancelled \(last 7 days\)/ }))
    expect(await within(screen.getByRole('tabpanel')).findByText('R-2026-0007')).toBeInTheDocument()
  })
})

describe('ReceiptsPage given / booked state on the Active Rentals card', () => {
  it('shows a blue "Given to customer" tick on a GIVEN card and no Mark as Given button', async () => {
    stubReceiptLists({ active: [aSummary({ status: 'GIVEN' })] })

    renderReceiptsPage()
    await flush()

    expect(await screen.findByLabelText('Given to customer')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Mark as Given$/ })).not.toBeInTheDocument()
    expect(screen.queryByText('Booked')).not.toBeInTheDocument()
  })

  it('shows a Booked tag and a Mark as Given button on a GENERATED card, with no tick', async () => {
    stubReceiptLists({ active: [aSummary({ status: 'GENERATED' })] })

    renderReceiptsPage()
    await flush()

    expect(await screen.findByText('Booked')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Mark as Given$/ })).toBeInTheDocument()
    expect(screen.queryByLabelText('Given to customer')).not.toBeInTheDocument()
  })

  it('swaps the Booked tag for the tick after Mark as Given is clicked', async () => {
    let isGiven = false
    server.use(
      http.get('*/api/receipts', ({ request }) => {
        if (new URL(request.url).searchParams.get('status') === 'CANCELLED') return ok([])
        return ok([aSummary({ status: isGiven ? 'GIVEN' : 'GENERATED' })])
      }),
      http.post('*/api/receipts/rcpt-1/give', () => {
        isGiven = true
        return ok(f.aReceipt({ status: 'GIVEN' }))
      }),
    )
    const user = userEvent.setup()
    renderReceiptsPage()
    await flush()

    await user.click(await screen.findByRole('button', { name: /Mark as Given$/ }))

    expect(await screen.findByLabelText('Given to customer')).toBeInTheDocument()
    expect(screen.queryByText('Booked')).not.toBeInTheDocument()
  })
})

describe('ReceiptsPage search by customer or costume name', () => {
  async function renderAndSearch(query: string) {
    const user = userEvent.setup()
    renderReceiptsPage()
    await user.type(await screen.findByRole('textbox', { name: 'Search rentals' }), query)
    return user
  }

  it('keeps only the rentals whose customer name matches', async () => {
    stubReceiptLists({ active: [
      aSummary({ id: 'a', receiptNumber: 'R-1', customerName: 'Priya Sharma' }),
      aSummary({ id: 'b', receiptNumber: 'R-2', customerName: 'Meera' }),
    ] })

    await renderAndSearch('priya')

    expect(screen.getByText('R-1')).toBeInTheDocument()
    expect(screen.queryByText('R-2')).not.toBeInTheDocument()
  })

  it('finds a rental by costume name, opening its pickup date even when that date starts folded', async () => {
    stubReceiptLists({ active: [
      aSummary({ id: 'a', receiptNumber: 'R-1', itemNames: ['Red Lehenga ×1'], startDatetime: dayjs().add(10, 'day').toISOString() }),
      aSummary({ id: 'b', receiptNumber: 'R-2', itemNames: ['Royal Sherwani ×1'] }),
    ] })

    await renderAndSearch('lehenga')

    expect(await screen.findByText('R-1')).toBeInTheDocument()
    expect(screen.queryByText('R-2')).not.toBeInTheDocument()
  })

  it('applies the search to the Cancelled tab too', async () => {
    stubReceiptLists({ active: [], cancelled: [
      aSummary({ id: 'c1', receiptNumber: 'R-7', customerName: 'Priya', status: 'CANCELLED' }),
      aSummary({ id: 'c2', receiptNumber: 'R-8', customerName: 'Meera', status: 'CANCELLED' }),
    ] })

    const user = await renderAndSearch('meera')
    await user.click(screen.getByRole('tab', { name: /Cancelled \(last 7 days\)/ }))

    const panel = within(screen.getByRole('tabpanel'))
    expect(await panel.findByText('R-8')).toBeInTheDocument()
    expect(panel.queryByText('R-7')).not.toBeInTheDocument()
  })

  it('says nothing matched instead of claiming there are no active rentals', async () => {
    stubReceiptLists({ active: [aSummary({ customerName: 'Meera' })] })

    await renderAndSearch('anarkali')

    expect(screen.getByText('No rentals match "anarkali"')).toBeInTheDocument()
    expect(screen.queryByText('No active rentals')).not.toBeInTheDocument()
  })
})

describe('ReceiptsPage groups active rentals by pickup date', () => {
  it('shows a heading per pickup date, from past to future', async () => {
    stubReceiptLists({ active: [
      aSummary({ id: 'later', startDatetime: dayjs().add(5, 'day').toISOString() }),
      aSummary({ id: 'today' }),
      aSummary({ id: 'past', startDatetime: dayjs().subtract(3, 'day').toISOString() }),
    ] })

    renderReceiptsPage()

    const headings = await waitFor(() => {
      const dateHeadings = screen.getAllByRole('button').filter(el => el.hasAttribute('aria-expanded'))
      expect(dateHeadings).toHaveLength(3)
      return dateHeadings.map(el => el.textContent)
    })
    expect(headings[0]).toMatch(/picked up/)
    expect(headings[1]).toMatch(/^Today/)
  })

  it("shows tomorrow's pickups straight away but keeps a later date folded", async () => {
    stubReceiptLists({ active: [
      aSummary({ id: 'tomorrow', receiptNumber: 'R-TOMORROW', startDatetime: dayjs().add(1, 'day').toISOString() }),
      aSummary({ id: 'later', receiptNumber: 'R-LATER', startDatetime: dayjs().add(10, 'day').toISOString() }),
    ] })

    renderReceiptsPage()

    expect(await screen.findByText('R-TOMORROW')).toBeInTheDocument()
    expect(screen.queryByText('R-LATER')).not.toBeInTheDocument()
  })

  it('keeps a past pickup date with an overdue rental open, so a late return is not hidden', async () => {
    stubReceiptLists({ active: [aSummary({
      receiptNumber: 'R-LATE', startDatetime: dayjs().subtract(4, 'day').toISOString(), isOverdue: true, overdueHours: 30,
    })] })

    renderReceiptsPage()

    expect(await screen.findByText('R-LATE')).toBeInTheDocument()
  })

  it('opens a folded date when its heading is tapped', async () => {
    const later = dayjs().add(10, 'day')
    stubReceiptLists({ active: [aSummary({ receiptNumber: 'R-LATER', startDatetime: later.toISOString() })] })
    const user = userEvent.setup()

    renderReceiptsPage()
    await user.click(await screen.findByText(new RegExp(later.format('D MMM'))))

    expect(await screen.findByText('R-LATER')).toBeInTheDocument()
  })
})

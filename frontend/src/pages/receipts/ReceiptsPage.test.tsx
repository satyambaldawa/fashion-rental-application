import { describe, it, expect, beforeEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import { MemoryRouter, Route, Routes, useParams } from 'react-router-dom'
import dayjs from 'dayjs'
import { flush, screen, within } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import { useAuthStore } from '../../store/authStore'
import { jwtWithRole } from '../../test/auth'
import ReceiptsPage from './ReceiptsPage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })
const failure = () => HttpResponse.json({ success: false, data: null, error: 'boom' }, { status: 500 })

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
    server.use(http.get('*/api/receipts', () => ok([f.aReceiptSummary({
      totalRent: 300, totalDeposit: 1000, grandTotal: 1300, couponCode: null, discountAmount: 0,
    })])))

    renderReceiptsPage()
    await flush()

    expect(within(screen.getByText('Rent').parentElement!).getByText('₹300')).toBeInTheDocument()
    expect(within(screen.getByText('Deposit').parentElement!).getByText('₹1,000')).toBeInTheDocument()
    expect(within(screen.getByText('Total').parentElement!).getByText('₹1,300')).toBeInTheDocument()
    expect(screen.queryByText(/SAVE/)).not.toBeInTheDocument()
  })

  it('shows the pre-discount Rent, the coupon tag and the backend grandTotal when a coupon is applied', async () => {
    server.use(http.get('*/api/receipts', () => ok([f.aReceiptSummary({
      totalRent: 1000, discountAmount: 100, couponCode: 'SAVE100', totalDeposit: 500, grandTotal: 1400,
    })])))

    renderReceiptsPage()
    await flush()

    expect(within(screen.getByText('Rent').parentElement!).getByText('₹1,000')).toBeInTheDocument()
    expect(within(screen.getByText('Deposit').parentElement!).getByText('₹500')).toBeInTheDocument()
    expect(within(screen.getByText('Total').parentElement!).getByText('₹1,400')).toBeInTheDocument()
    expect(screen.getByText('SAVE100 −₹100')).toBeInTheDocument()
  })

  it('shows grandTotal exactly as returned, even when it is not Rent + Deposit', async () => {
    server.use(http.get('*/api/receipts', () => ok([f.aReceiptSummary({
      totalRent: 300, totalDeposit: 1000, grandTotal: 1250, couponCode: null, discountAmount: 0,
    })])))

    renderReceiptsPage()
    await flush()

    expect(within(screen.getByText('Total').parentElement!).getByText('₹1,250')).toBeInTheDocument()
    expect(screen.queryByText('₹1,300')).not.toBeInTheDocument()
  })

  it('shows correct labelled amounts on the Overdue tab', async () => {
    server.use(http.get('*/api/receipts', () => ok([f.aReceiptSummary({
      id: 'rcpt-overdue', isOverdue: true, overdueHours: 5,
      totalRent: 450, totalDeposit: 800, grandTotal: 1250, couponCode: null, discountAmount: 0,
    })])))

    const user = userEvent.setup()
    renderReceiptsPage()
    await flush()

    await user.click(screen.getByRole('tab', { name: /overdue/i }))

    const panel = within(screen.getByRole('tabpanel'))
    expect(within(panel.getByText('Rent').parentElement!).getByText('₹450')).toBeInTheDocument()
    expect(within(panel.getByText('Deposit').parentElement!).getByText('₹800')).toBeInTheDocument()
    expect(within(panel.getByText('Total').parentElement!).getByText('₹1,250')).toBeInTheDocument()
  })

  it('replaces Process Return with a View button', async () => {
    server.use(http.get('*/api/receipts', () => ok([f.aReceiptSummary()])))

    renderReceiptsPage()
    await flush()

    expect(screen.queryByRole('button', { name: /process return/i })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /view/i })).toBeInTheDocument()
  })

  it('navigates to the receipt detail page when View is clicked', async () => {
    server.use(http.get('*/api/receipts', () => ok([f.aReceiptSummary({ id: 'rcpt-42' })])))

    const user = userEvent.setup()
    renderReceiptsPage()
    await flush()

    await user.click(screen.getByRole('button', { name: /view/i }))

    expect(await screen.findByRole('heading', { name: 'Receipt detail stub' })).toBeInTheDocument()
  })

  it('navigates to that specific receipt\'s id, not just any detail route', async () => {
    server.use(http.get('*/api/receipts', () => ok([f.aReceiptSummary({ id: 'rcpt-99' })])))

    const user = userEvent.setup()
    renderReceiptsPage(<ReceiptIdStub />)
    await flush()

    await user.click(screen.getByRole('button', { name: /view/i }))

    expect(await screen.findByRole('heading', { name: 'Receipt detail: rcpt-99' })).toBeInTheDocument()
  })

  it('gives the View button a 44px tap target', async () => {
    server.use(http.get('*/api/receipts', () => ok([f.aReceiptSummary()])))

    renderReceiptsPage()
    await flush()

    expect(screen.getByRole('button', { name: /view/i })).toHaveStyle({ minHeight: '44px' })
  })
})

function stubReceiptLists({ active = [f.aReceiptSummary()], cancelled = [] as unknown[] } = {}) {
  server.use(http.get('*/api/receipts', ({ request }) => {
    const status = new URL(request.url).searchParams.get('status')
    return ok(status === 'CANCELLED' ? cancelled : active)
  }))
}

async function renderAndOpenCancelledTab() {
  renderReceiptsPage()
  await flush()
  await userEvent.setup().click(screen.getByRole('tab', { name: /Cancelled \(last 7 days\)/ }))
}

describe('ReceiptsPage cancelled tab (#166)', () => {
  it('lists recently cancelled receipts with a Cancelled tag and a View button', async () => {
    stubReceiptLists({
      cancelled: [f.aReceiptSummary({ id: 'rcpt-9', receiptNumber: 'R-2026-0009', status: 'CANCELLED' })],
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
      new URL(request.url).searchParams.get('status') === 'CANCELLED' ? failure() : ok([f.aReceiptSummary()])))

    await renderAndOpenCancelledTab()

    expect(await screen.findByText('Failed to load cancelled receipts. Please try again.')).toBeInTheDocument()
    expect(screen.queryByText('No receipts cancelled in the last 7 days')).not.toBeInTheDocument()
  })
})

describe('ReceiptsPage cancel from the Active Rentals card', () => {
  // Well clear of the 12-hour cutoff whenever the suite runs.
  const ELIGIBLE_END = dayjs().add(2, 'day').toISOString()

  it('offers a red bin Cancel receipt button, as tall as View, to an OWNER on an eligible card', async () => {
    stubReceiptLists({ active: [f.aReceiptSummary({ endDatetime: ELIGIBLE_END })] })

    renderReceiptsPage()
    await flush()

    expect(await screen.findByRole('button', { name: 'Cancel receipt' })).toHaveStyle({ height: '44px' })
  })

  it('hides the Cancel receipt button from an EXECUTIVE', async () => {
    useAuthStore.setState({ token: jwtWithRole('EXECUTIVE'), role: 'EXECUTIVE' })
    stubReceiptLists({ active: [f.aReceiptSummary({ endDatetime: ELIGIBLE_END })] })

    renderReceiptsPage()
    await flush()

    expect(await screen.findByRole('button', { name: /view/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel receipt' })).not.toBeInTheDocument()
  })

  it('hides the Cancel receipt button when the rental ends within 12 hours', async () => {
    stubReceiptLists({ active: [f.aReceiptSummary({ endDatetime: dayjs().add(11, 'hour').toISOString() })] })

    renderReceiptsPage()
    await flush()

    expect(await screen.findByRole('button', { name: /view/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel receipt' })).not.toBeInTheDocument()
  })

  it('cancels from the card and moves the receipt to the Cancelled tab', async () => {
    const summary = f.aReceiptSummary({ id: 'rcpt-7', receiptNumber: 'R-2026-0007', endDatetime: ELIGIBLE_END })
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

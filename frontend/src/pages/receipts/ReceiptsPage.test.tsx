import { describe, it, expect, beforeEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import { MemoryRouter, Route, Routes, useParams } from 'react-router-dom'
import { flush, screen, within } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import { useAuthStore } from '../../store/authStore'
import ReceiptsPage from './ReceiptsPage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })

beforeEach(() => useAuthStore.setState({ token: 'test-token', role: 'OWNER' }))

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

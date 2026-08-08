import { describe, it, expect } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { renderWithProviders, flush, screen } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import CustomerDetailPage from './CustomerDetailPage'

const renderPage = () =>
  renderWithProviders(<CustomerDetailPage />, {
    route: '/customers/cust-1',
    path: '/customers/:id',
  })

// Mounts a real /invoices/:id route so a test can confirm the invoice link
// actually navigates there, rather than just not-crashing.
const renderPageWithInvoiceRoute = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ConfigProvider>
        <MemoryRouter initialEntries={['/customers/cust-1']}>
          <Routes>
            <Route path="/customers/:id" element={<CustomerDetailPage />} />
            <Route path="/invoices/:id" element={<div>Invoice Detail Page</div>} />
          </Routes>
        </MemoryRouter>
      </ConfigProvider>
    </QueryClientProvider>,
  )
}

describe('CustomerDetailPage', () => {
  it('shows a loading spinner while the history request is in flight', async () => {
    server.use(
      http.get('*/api/customers/:id/history', async () => {
        await new Promise(resolve => setTimeout(resolve, 50))
        return HttpResponse.json({ success: true, data: f.aCustomerDetail(), error: null })
      }),
    )

    const { container } = renderPage()

    expect(container.querySelector('.ant-spin')).toBeInTheDocument()
  })

  it('shows an error alert when the history request fails', async () => {
    server.use(
      http.get('*/api/customers/:id/history', () =>
        HttpResponse.json({ success: false, data: null, error: 'Not found' }, { status: 404 })),
    )

    renderPage()
    await flush()

    expect(await screen.findByText(/Failed to load customer history/)).toBeInTheDocument()
  })

  it('shows a no-history message and a neutral zero-outstanding banner when the customer has no receipts', async () => {
    server.use(
      http.get('*/api/customers/:id/history', () =>
        HttpResponse.json({
          success: true,
          data: f.aCustomerDetail({ outstandingDeposit: 0, receipts: [] }),
          error: null,
        })),
    )

    renderPage()
    await flush()

    expect(await screen.findByText('No rental history yet.')).toBeInTheDocument()
    expect(screen.getByText('Outstanding Deposit:')).toBeInTheDocument()
    expect(screen.getByText('₹0')).toBeInTheDocument()
  })

  it('renders the outstanding deposit with warning styling when non-zero', async () => {
    server.use(
      http.get('*/api/customers/:id/history', () =>
        HttpResponse.json({
          success: true,
          data: f.aCustomerDetail({ outstandingDeposit: 1500, receipts: [f.aCustomerReceipt()] }),
          error: null,
        })),
    )

    renderPage()
    await flush()

    const banner = await screen.findByRole('alert')
    expect(banner).toHaveClass('ant-alert-warning')
    expect(banner).toHaveTextContent('Outstanding Deposit: ₹1,500')
  })

  it('shows an invoice link for a RETURNED receipt and navigates to the invoice page', async () => {
    const user = userEvent.setup()
    server.use(
      http.get('*/api/customers/:id/history', () =>
        HttpResponse.json({
          success: true,
          data: f.aCustomerDetail({
            outstandingDeposit: 0,
            receipts: [f.aCustomerReceipt({
              status: 'RETURNED',
              invoice: { id: 'inv-1', invoiceNumber: 'INV-20260412-001', finalAmount: 50, transactionType: 'COLLECT' },
            })],
          }),
          error: null,
        })),
    )

    renderPageWithInvoiceRoute()
    await flush()

    expect(await screen.findByText(/INV-20260412-001/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'View Invoice' }))

    expect(await screen.findByText('Invoice Detail Page')).toBeInTheDocument()
  })

  it('shows no invoice link for a GIVEN receipt', async () => {
    server.use(
      http.get('*/api/customers/:id/history', () =>
        HttpResponse.json({
          success: true,
          data: f.aCustomerDetail({
            outstandingDeposit: 1500,
            receipts: [f.aCustomerReceipt({ status: 'GIVEN', invoice: null })],
          }),
          error: null,
        })),
    )

    renderPage()
    await flush()

    expect(await screen.findByText('R-20260418-003')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'View Invoice' })).not.toBeInTheDocument()
  })

  it('renders items as a flat name times quantity list with no package sub-structure', async () => {
    server.use(
      http.get('*/api/customers/:id/history', () =>
        HttpResponse.json({
          success: true,
          data: f.aCustomerDetail({
            outstandingDeposit: 0,
            receipts: [f.aCustomerReceipt({
              items: [
                { itemName: 'Wedding Package', quantity: 1 },
                { itemName: 'Blue Sherwani', quantity: 2 },
              ],
            })],
          }),
          error: null,
        })),
    )

    renderPage()
    await flush()

    expect(await screen.findByText('Wedding Package ×1')).toBeInTheDocument()
    expect(screen.getByText('Blue Sherwani ×2')).toBeInTheDocument()
  })
})

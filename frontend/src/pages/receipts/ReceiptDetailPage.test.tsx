import { describe, it, expect, beforeEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import { renderWithProviders, flush, screen } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import { useAuthStore } from '../../store/authStore'
import ReceiptDetailPage from './ReceiptDetailPage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })

beforeEach(() => useAuthStore.setState({ token: 'test-token', role: 'OWNER' }))

async function renderReceipt() {
  renderWithProviders(<ReceiptDetailPage />, { route: '/receipts/rcpt-1', path: '/receipts/:id' })
  await flush()
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

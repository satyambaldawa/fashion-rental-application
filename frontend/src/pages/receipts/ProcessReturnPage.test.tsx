import { describe, it, expect, beforeEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import { renderWithProviders, flush, screen } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import { useAuthStore } from '../../store/authStore'
import ProcessReturnPage from './ProcessReturnPage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })

beforeEach(() => useAuthStore.setState({ token: 'test-token', role: 'OWNER' }))

async function renderProcessReturn() {
  renderWithProviders(<ProcessReturnPage />, { route: '/receipts/rcpt-1/return', path: '/receipts/:id/return' })
  await flush()
}

describe('ProcessReturnPage discount row', () => {
  it('shows the discount when the receipt has a coupon applied, in the receipt summary', async () => {
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ couponCode: 'SAVE20', discountAmount: 60, totalRent: 240 }))))

    await renderProcessReturn()

    expect(await screen.findByText('Discount (SAVE20)')).toBeInTheDocument()
    expect(screen.getByText('−₹60')).toBeInTheDocument()
  })

  it('omits the discount row when no coupon was applied', async () => {
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ couponCode: null, discountAmount: 0 }))))

    await renderProcessReturn()

    expect(await screen.findByText('Rent Charged')).toBeInTheDocument()
    expect(screen.queryByText(/Discount \(/)).not.toBeInTheDocument()
  })
})

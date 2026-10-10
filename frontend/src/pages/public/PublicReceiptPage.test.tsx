import { describe, it, expect } from 'vitest'
import { http, HttpResponse } from 'msw'
import { renderWithProviders, flush, screen } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import PublicReceiptPage from './PublicReceiptPage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })

async function renderPublicReceipt() {
  renderWithProviders(<PublicReceiptPage />, {
    route: '/public/receipts/share-r',
    path: '/public/receipts/:shareToken',
  })
  await flush()
}

describe('PublicReceiptPage (#166)', () => {
  it('shows a CANCELLED tag and banner for a cancelled receipt, with no deposit-refundable callout', async () => {
    server.use(http.get('*/api/public/receipts/share-r', () =>
      ok(f.aReceipt({ status: 'CANCELLED', cancellation: null, grandTotal: 1300 }))))

    await renderPublicReceipt()

    expect(await screen.findByText('This receipt has been cancelled.')).toBeInTheDocument()
    expect(screen.getByText('CANCELLED')).toBeInTheDocument()
    expect(screen.getByText('Amount refunded')).toBeInTheDocument()
    expect(screen.queryByText('Deposit refundable on return')).not.toBeInTheDocument()
  })

  it('shows the normal deposit-refundable callout and no cancellation banner for a GIVEN receipt', async () => {
    server.use(http.get('*/api/public/receipts/share-r', () => ok(f.aReceipt({ status: 'GIVEN' }))))

    await renderPublicReceipt()

    expect(await screen.findByText('Deposit refundable on return')).toBeInTheDocument()
    expect(screen.queryByText('This receipt has been cancelled.')).not.toBeInTheDocument()
    expect(screen.queryByText('CANCELLED')).not.toBeInTheDocument()
  })
})

import { describe, it, expect } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { renderWithProviders, flush, screen } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import ReceiptsPage from './ReceiptsPage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })
const failure = () => HttpResponse.json({ success: false, data: null, error: 'boom' }, { status: 500 })

function stubReceiptLists({ active = [f.aReceiptSummary()], cancelled = [] as unknown[] } = {}) {
  server.use(http.get('*/api/receipts', ({ request }) => {
    const status = new URL(request.url).searchParams.get('status')
    return ok(status === 'CANCELLED' ? cancelled : active)
  }))
}

async function renderAndOpenCancelledTab() {
  renderWithProviders(<ReceiptsPage />)
  await flush()
  await userEvent.setup().click(screen.getByRole('tab', { name: /Cancelled \(last 7 days\)/ }))
}

describe('ReceiptsPage cancelled tab (#166)', () => {
  it('lists recently cancelled receipts with a Cancelled tag and a View button', async () => {
    stubReceiptLists({
      cancelled: [f.aReceiptSummary({ id: 'rcpt-9', receiptNumber: 'R-2026-0009', status: 'CANCELLED' })],
    })

    await renderAndOpenCancelledTab()

    expect(await screen.findByText('R-2026-0009')).toBeInTheDocument()
    expect(screen.getByText('Cancelled')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'View' })).toBeInTheDocument()
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

describe('ReceiptsPage active tab', () => {
  it('offers Process Return for a Given receipt', async () => {
    stubReceiptLists({ active: [f.aReceiptSummary({ status: 'GIVEN' })] })

    renderWithProviders(<ReceiptsPage />)
    await flush()

    expect(await screen.findByRole('button', { name: 'Process Return' })).toBeInTheDocument()
  })
})

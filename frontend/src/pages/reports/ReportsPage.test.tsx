import { describe, it, expect } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { renderWithProviders, flush, screen } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import { Route, Routes, useLocation } from 'react-router-dom'
import type { InvoiceBackLink } from '../../types/invoice'
import ReportsPage from './ReportsPage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })

async function renderAndOpenDailyTab() {
  renderWithProviders(<ReportsPage />)
  await flush()
  await userEvent.setup().click(screen.getByRole('tab', { name: 'Daily Revenue' }))
  await flush()
}

describe('ReportsPage daily revenue cancellations (#166)', () => {
  it('shows the refunded amount as a deduction on a day with cancellations', async () => {
    server.use(http.get('*/api/reports/daily-revenue', () =>
      ok(f.aDailyRevenue({ cancellationsCount: 2, cancellationRefunds: 2600 }))))

    await renderAndOpenDailyTab()

    expect(await screen.findByText('Cancellations (2 receipts)')).toBeInTheDocument()
    expect(screen.getByText('Refunded on cancellation')).toBeInTheDocument()
    expect(screen.getByText('−₹2,600')).toBeInTheDocument()
  })

  it('hides the cancellations section on a day without cancellations', async () => {
    server.use(http.get('*/api/reports/daily-revenue', () =>
      ok(f.aDailyRevenue({ cancellationsCount: 0, cancellationRefunds: 0 }))))

    await renderAndOpenDailyTab()

    expect(await screen.findByText(/Summary —/)).toBeInTheDocument()
    expect(screen.queryByText(/Cancellations \(/)).not.toBeInTheDocument()
  })
})

describe('ReportsPage monthly revenue cancellations (#166)', () => {
  it('shows the month total of cancellation refunds', async () => {
    server.use(http.get('*/api/reports/monthly-revenue', () =>
      ok(f.aMonthlyRevenue({ totalCancellationRefunds: 2750 }))))

    renderWithProviders(<ReportsPage />)
    await flush()

    expect(await screen.findByText('Cancellation Refunds')).toBeInTheDocument()
    expect(screen.getByText('₹2,750')).toBeInTheDocument()
  })
})

describe('ReportsPage invoices tab', () => {
  const INVOICES_ROUTE = '/reports?tab=invoices'
  const DEBOUNCE_SETTLE_MS = 600

  function InvoiceDetailStub() {
    const state = useLocation().state as InvoiceBackLink | null
    return <div>Invoice detail page, back to {state?.backTo} ({state?.backLabel})</div>
  }

  /** Serves `report` for every invoices request and records each request's query string. */
  function mockInvoicesReport(report = f.anInvoicesReport()): URLSearchParams[] {
    const requests: URLSearchParams[] = []
    server.use(http.get('*/api/reports/invoices', ({ request }) => {
      requests.push(new URL(request.url).searchParams)
      return ok(report)
    }))
    return requests
  }

  async function renderInvoicesTab(route = INVOICES_ROUTE) {
    renderWithProviders(
      <Routes>
        <Route path="/reports" element={<ReportsPage />} />
        <Route path="/invoices/:id" element={<InvoiceDetailStub />} />
      </Routes>,
      { route },
    )
    await flush()
  }

  it('opens on the Invoices tab when the URL names it', async () => {
    mockInvoicesReport()

    await renderInvoicesTab()

    expect(screen.getByRole('tab', { name: 'Invoices', selected: true })).toBeInTheDocument()
  })

  it('lists invoices with item counts and sums the breakdown below them', async () => {
    const report = f.anInvoicesReport()
    mockInvoicesReport(report)

    await renderInvoicesTab()

    expect(await screen.findByText(report.invoices[0].invoiceNumber)).toBeInTheDocument()
    expect(screen.getByText(report.invoices[0].customerName)).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Invoiced on' })).toBeInTheDocument()
    expect(screen.getByText('15 Apr 2026')).toBeInTheDocument()
    expect(screen.getByText('1 invoice')).toBeInTheDocument()
    expect(screen.getByText('Total items rented')).toBeInTheDocument()
    expect(screen.getByText(/Revenue is counted on the invoice date/)).toBeInTheDocument()
    expect(screen.getByText(`−₹${report.totalDiscount}`)).toBeInTheDocument()
    // The row's revenue and the footer's total revenue are the same figure for a one-invoice report.
    expect(screen.getAllByText('₹1,550')).toHaveLength(2)
  })

  it('requests the page, range and minimum carried in the URL', async () => {
    const requests = mockInvoicesReport()

    await renderInvoicesTab(`${INVOICES_ROUTE}&from=2026-04-01&to=2026-04-30&min=500&page=3`)
    await flush(DEBOUNCE_SETTLE_MS)

    const last = requests[requests.length - 1]
    expect(last.get('from')).toBe('2026-04-01')
    expect(last.get('to')).toBe('2026-04-30')
    expect(last.get('minRevenue')).toBe('500')
    expect(last.get('page')).toBe('2') // URL pages are 1-based, the API's are 0-based
    expect(last.get('size')).toBe('20')
  })

  it('re-queries with the minimum revenue once typed', async () => {
    const requests = mockInvoicesReport()

    await renderInvoicesTab()
    await userEvent.setup().type(screen.getByRole('spinbutton', { name: 'Min revenue' }), '1000')
    await flush(DEBOUNCE_SETTLE_MS)

    expect(requests.map(r => r.get('minRevenue'))).toContain('1000')
  })

  it('Clear drops the minimum and re-queries without it', async () => {
    const requests = mockInvoicesReport()

    await renderInvoicesTab(`${INVOICES_ROUTE}&min=500`)
    await flush(DEBOUNCE_SETTLE_MS)
    await userEvent.setup().click(screen.getByRole('button', { name: 'Clear' }))
    await flush(DEBOUNCE_SETTLE_MS)

    expect(requests[0].get('minRevenue')).toBe('500')
    expect(requests[requests.length - 1].get('minRevenue')).toBeNull()
    expect(screen.getByRole('spinbutton', { name: 'Min revenue' })).toHaveValue('')
  })

  it('requests the next page from the API when the pager is used', async () => {
    const requests = mockInvoicesReport(f.anInvoicesReport({ invoiceCount: 45, totalPages: 3 }))

    await renderInvoicesTab()
    await userEvent.setup().click(await screen.findByTitle('2'))
    await flush()

    expect(requests[requests.length - 1].get('page')).toBe('1')
  })

  it('shows the backend validation message when the request is rejected', async () => {
    const rangeTooLong = 'Date range must not exceed 366 days.'
    server.use(http.get('*/api/reports/invoices', () =>
      HttpResponse.json({ success: false, data: null, error: rangeTooLong }, { status: 400 })))

    await renderInvoicesTab(`${INVOICES_ROUTE}&from=2024-01-01&to=2026-01-01`)

    expect(await screen.findByText(rangeTooLong)).toBeInTheDocument()
  })

  it('View opens the invoice with a back link to this exact filtered list', async () => {
    const filteredRoute = `${INVOICES_ROUTE}&from=2026-04-01&to=2026-04-30`
    mockInvoicesReport()

    await renderInvoicesTab(filteredRoute)
    await userEvent.setup().click(await screen.findByRole('button', { name: 'View INV-0042' }))

    expect(await screen.findByText(`Invoice detail page, back to ${filteredRoute} (Back to Invoices)`))
      .toBeInTheDocument()
  })

  it('shows the empty state when no invoice matches', async () => {
    mockInvoicesReport(f.anInvoicesReport({
      invoices: [], invoiceCount: 0, totalItems: 0, totalRent: 0, totalDiscount: 0,
      totalLateFee: 0, totalDamage: 0, totalRevenue: 0,
    }))

    await renderInvoicesTab()

    expect(await screen.findByText('No invoices for these filters')).toBeInTheDocument()
  })
})

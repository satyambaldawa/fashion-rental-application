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
  function InvoiceDetailStub() {
    const state = useLocation().state as InvoiceBackLink | null
    return <div>Invoice detail page, back to {state?.backTo} ({state?.backLabel})</div>
  }

  async function renderInvoicesTab(route = '/reports?tab=invoices') {
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
    server.use(http.get('*/api/reports/invoices', () => ok(f.anInvoicesReport())))

    await renderInvoicesTab()

    expect(screen.getByRole('tab', { name: 'Invoices', selected: true })).toBeInTheDocument()
  })

  it('lists invoices with item counts and sums the breakdown below them', async () => {
    server.use(http.get('*/api/reports/invoices', () =>
      ok(f.anInvoicesReport({ invoiceCount: 1, totalItems: 2 }))))

    await renderInvoicesTab()

    expect(await screen.findByText('INV-0042')).toBeInTheDocument()
    expect(screen.getByText('Ravi Kumar')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Invoiced on' })).toBeInTheDocument()
    expect(screen.getByText('15 Apr 2026')).toBeInTheDocument()
    expect(screen.getByText('1 invoice')).toBeInTheDocument()
    expect(screen.getByText('Total items rented')).toBeInTheDocument()
    expect(screen.getByText(/Revenue is counted on the invoice date/)).toBeInTheDocument()
    expect(screen.getByText('−₹100')).toBeInTheDocument()
    expect(screen.getAllByText('₹1,550')).toHaveLength(2)
  })

  it('requests the page, range and minimum carried in the URL', async () => {
    const requests: URLSearchParams[] = []
    server.use(http.get('*/api/reports/invoices', ({ request }) => {
      requests.push(new URL(request.url).searchParams)
      return ok(f.anInvoicesReport())
    }))

    await renderInvoicesTab('/reports?tab=invoices&from=2026-04-01&to=2026-04-30&min=500&page=3')
    await flush(600)

    const last = requests[requests.length - 1]
    expect(last.get('from')).toBe('2026-04-01')
    expect(last.get('to')).toBe('2026-04-30')
    expect(last.get('minRevenue')).toBe('500')
    expect(last.get('page')).toBe('2')
    expect(last.get('size')).toBe('20')
  })

  it('re-queries with the minimum revenue once typed', async () => {
    const requestedMinimums: (string | null)[] = []
    server.use(http.get('*/api/reports/invoices', ({ request }) => {
      requestedMinimums.push(new URL(request.url).searchParams.get('minRevenue'))
      return ok(f.anInvoicesReport())
    }))

    await renderInvoicesTab()
    await userEvent.setup().type(screen.getByRole('spinbutton', { name: 'Min revenue' }), '1000')
    await flush(600)

    expect(requestedMinimums).toContain('1000')
  })

  it('Clear drops the minimum and re-queries without it', async () => {
    const requestedMinimums: (string | null)[] = []
    server.use(http.get('*/api/reports/invoices', ({ request }) => {
      requestedMinimums.push(new URL(request.url).searchParams.get('minRevenue'))
      return ok(f.anInvoicesReport())
    }))

    await renderInvoicesTab('/reports?tab=invoices&min=500')
    await flush(600)
    await userEvent.setup().click(screen.getByRole('button', { name: 'Clear' }))
    await flush(600)

    expect(requestedMinimums[0]).toBe('500')
    expect(requestedMinimums[requestedMinimums.length - 1]).toBeNull()
    expect(screen.getByRole('spinbutton', { name: 'Min revenue' })).toHaveValue('')
  })

  it('View opens the invoice with a back link to this exact filtered list', async () => {
    server.use(http.get('*/api/reports/invoices', () => ok(f.anInvoicesReport())))

    await renderInvoicesTab('/reports?tab=invoices&from=2026-04-01&to=2026-04-30')
    await userEvent.setup().click(await screen.findByRole('button', { name: 'View INV-0042' }))

    expect(await screen.findByText(
      'Invoice detail page, back to /reports?tab=invoices&from=2026-04-01&to=2026-04-30 (Back to Invoices)',
    )).toBeInTheDocument()
  })

  it('shows the empty state when no invoice matches', async () => {
    server.use(http.get('*/api/reports/invoices', () =>
      ok(f.anInvoicesReport({ invoices: [], invoiceCount: 0, totalItems: 0, totalRent: 0, totalDiscount: 0, totalLateFee: 0, totalDamage: 0, totalRevenue: 0 }))))

    await renderInvoicesTab()

    expect(await screen.findByText('No invoices for these filters')).toBeInTheDocument()
  })
})

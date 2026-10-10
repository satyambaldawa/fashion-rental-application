import { describe, it, expect } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { renderWithProviders, flush, screen } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
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

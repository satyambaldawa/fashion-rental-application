import { describe, it, expect, beforeEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import { renderWithProviders, flush, screen } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import { Route, Routes } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { useAuthStore } from '../../store/authStore'
import type { InvoiceBackLink } from '../../types/invoice'
import InvoiceDetailPage from './InvoiceDetailPage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })

beforeEach(() => useAuthStore.setState({ token: 'test-token', role: 'OWNER' }))

async function renderInvoice() {
  renderWithProviders(<InvoiceDetailPage />, { route: '/invoices/inv-1', path: '/invoices/:id' })
  await flush()
}

// "Rent Charged"'s value can collide with the line-item table's Rate/day column (both ₹300
// in these fixtures), so read the specific Descriptions.Item rather than plain getByText.
// bordered Descriptions renders as a <table> with the label <th> and value <td> as siblings
// in the same <tr>, unlike the non-bordered variant used elsewhere in the app.
function descriptionValue(label: string): string {
  const labelEl = screen.getByText(label)
  const row = labelEl.closest('tr')!
  return row.querySelector('.ant-descriptions-item-content')!.textContent!
}

describe('InvoiceDetailPage discount row', () => {
  it('renders Rent Charged as the gross figure and a discount row, not the stored net totalRent', async () => {
    // invoice.totalRent is persisted net of the discount (the customer never paid the
    // gross figure) — the page must derive gross back for display so "Rent Charged" means
    // the same thing here as it does on the receipt this invoice came from.
    server.use(http.get('*/api/invoices/inv-1', () =>
      ok(f.anInvoice({ couponCode: 'SAVE20', discountAmount: 60, totalRent: 240 }))))

    await renderInvoice()

    expect(await screen.findByText('Discount (SAVE20)')).toBeInTheDocument()
    expect(descriptionValue('Rent Charged')).toBe('₹300') // 240 net + 60 discount = 300 gross
    expect(descriptionValue(`Discount (SAVE20)`)).toBe('−₹60')
  })

  it('omits the discount row and shows totalRent as-is when no coupon was applied', async () => {
    server.use(http.get('*/api/invoices/inv-1', () =>
      ok(f.anInvoice({ couponCode: null, discountAmount: 0, totalRent: 300 }))))

    await renderInvoice()

    expect(await screen.findByText('Rent Charged')).toBeInTheDocument()
    expect(screen.queryByText(/Discount \(/)).not.toBeInTheDocument()
    expect(descriptionValue('Rent Charged')).toBe('₹300')
  })
})

describe('InvoiceDetailPage late fee waived row (#184)', () => {
  it('shows "Late fee waived" when waivedLateFee is positive', async () => {
    server.use(http.get('*/api/invoices/inv-1', () =>
      ok(f.anInvoice({ totalLateFee: 0, waivedLateFee: 300 }))))

    await renderInvoice()

    expect(await screen.findByText('Late fee waived')).toBeInTheDocument()
    expect(descriptionValue('Late fee waived')).toBe('₹300')
  })

  it('omits "Late fee waived" when waivedLateFee is zero', async () => {
    server.use(http.get('*/api/invoices/inv-1', () =>
      ok(f.anInvoice({ waivedLateFee: 0 }))))

    await renderInvoice()

    expect(await screen.findByText('Rent Charged')).toBeInTheDocument()
    expect(screen.queryByText('Late fee waived')).not.toBeInTheDocument()
  })
})

describe('InvoiceDetailPage WhatsApp share', () => {
  it('includes a link to the review page in the WhatsApp message', async () => {
    server.use(http.get('*/api/invoices/inv-1', () => ok(f.anInvoice())))

    await renderInvoice()

    const link = await screen.findByRole('link', { name: /Send on WhatsApp/i })
    const decodedHref = decodeURIComponent(link.getAttribute('href') ?? '')

    expect(decodedHref).toContain(`${window.location.origin}/review`)
  })
})

describe('InvoiceDetailPage "Add items" (#165)', () => {
  it('has no Add items button — the action only exists on the receipt it was created from', async () => {
    await renderInvoice()

    expect(await screen.findByText('Rent Charged')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add items' })).not.toBeInTheDocument()
  })
})

describe('InvoiceDetailPage back link', () => {
  it('returns to Active Rentals when opened without a back link', async () => {
    server.use(http.get('*/api/invoices/inv-1', () => ok(f.anInvoice())))

    await renderInvoice()

    expect(await screen.findByRole('button', { name: /Back to Active Rentals/ })).toBeInTheDocument()
  })

  it('falls back to Active Rentals when the router state is not a back link', async () => {
    server.use(http.get('*/api/invoices/inv-1', () => ok(f.anInvoice())))

    renderWithProviders(<InvoiceDetailPage />, {
      route: '/invoices/inv-1', path: '/invoices/:id', state: { somethingElse: true },
    })
    await flush()

    expect(await screen.findByRole('button', { name: /Back to Active Rentals/ })).toBeInTheDocument()
  })

  it('offers the back link handed over by the page that opened it', async () => {
    server.use(http.get('*/api/invoices/inv-1', () => ok(f.anInvoice())))
    const backLink: InvoiceBackLink = { backTo: '/reports?tab=invoices', backLabel: 'Back to Invoices' }

    renderWithProviders(
      <Routes>
        <Route path="/invoices/:id" element={<InvoiceDetailPage />} />
        <Route path="/reports" element={<div>Invoices report</div>} />
      </Routes>,
      { route: '/invoices/inv-1', state: backLink },
    )
    await flush()
    await userEvent.setup().click(await screen.findByRole('button', { name: /Back to Invoices/ }))

    expect(await screen.findByText('Invoices report')).toBeInTheDocument()
  })
})

import { describe, it, expect } from 'vitest'
import { http, HttpResponse } from 'msw'
import { renderWithProviders, flush, screen } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import PublicInvoicePage from './PublicInvoicePage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })

async function renderPublicInvoice() {
  renderWithProviders(<PublicInvoicePage />, {
    route: '/public/invoices/share-i', path: '/public/invoices/:shareToken',
  })
  await flush()
}

// bordered Descriptions renders as a <table> with the label <th> and value <td> as siblings
// in the same <tr> (same technique as InvoiceDetailPage.test.tsx's descriptionValue).
function descriptionValue(label: string): string {
  const labelEl = screen.getByText(label)
  const row = labelEl.closest('tr')!
  return row.querySelector('.ant-descriptions-item-content')!.textContent!
}

describe('PublicInvoicePage late fee waived row (#184)', () => {
  it('shows "Late fee waived" when waivedLateFee is positive', async () => {
    server.use(http.get('*/api/public/invoices/share-i', () =>
      ok(f.anInvoice({ totalLateFee: 0, waivedLateFee: 300 }))))

    await renderPublicInvoice()

    expect(await screen.findByText('Late fee waived')).toBeInTheDocument()
    expect(descriptionValue('Late fee waived')).toBe('₹300')
  })

  it('omits "Late fee waived" when waivedLateFee is zero', async () => {
    server.use(http.get('*/api/public/invoices/share-i', () =>
      ok(f.anInvoice({ waivedLateFee: 0 }))))

    await renderPublicInvoice()

    expect(await screen.findByText(/Return Invoice/)).toBeInTheDocument()
    expect(screen.queryByText('Late fee waived')).not.toBeInTheDocument()
  })
})

import { describe, it, expect, beforeEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { renderWithProviders, flush, screen, waitFor } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import { useAuthStore } from '../../store/authStore'
import type { ReturnPreview } from '../../types/invoice'
import ProcessReturnPage from './ProcessReturnPage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })

beforeEach(() => useAuthStore.setState({ token: 'test-token', role: 'OWNER' }))

async function renderProcessReturn() {
  const result = renderWithProviders(<ProcessReturnPage />, { route: '/receipts/rcpt-1/return', path: '/receipts/:id/return' })
  await flush()
  return result
}

/** Shapes a preview response the way the backend would for the given decision and fee. */
function previewFor(body: { applyLateFee?: boolean }, calculatedLateFee: number): ReturnPreview {
  if (calculatedLateFee <= 0) {
    return f.aReturnPreview({
      calculatedLateFee: 0, totalLateFee: 0, waivedLateFee: 0,
      totalDeductions: 0, depositToReturn: 1000, finalAmount: 1000, transactionType: 'REFUND',
    })
  }
  const waived = body.applyLateFee === false
  const totalLateFee = waived ? 0 : calculatedLateFee
  const depositToReturn = 1000 - totalLateFee
  return f.aReturnPreview({
    calculatedLateFee, totalLateFee, waivedLateFee: waived ? calculatedLateFee : 0,
    totalDeductions: totalLateFee, depositToReturn, finalAmount: depositToReturn, transactionType: 'REFUND',
  })
}

function getDateInput(container: HTMLElement): HTMLInputElement {
  const input = container.querySelector('.ant-picker-input input')
  if (!input) throw new Error('return-date input not found')
  return input as HTMLInputElement
}

/** Drives the masked rc-picker input by focusing, clearing and typing the new value character
 * by character, then pressing Enter to commit — the input's own masked-paste handling isn't
 * reliably triggered by a synthetic paste event in jsdom (see plan risk #8). */
async function changeReturnDateTime(user: ReturnType<typeof userEvent.setup>, container: HTMLElement, value: string) {
  const input = getDateInput(container)
  await user.click(input)
  await user.clear(input)
  await user.type(input, value)
  await user.keyboard('{Enter}')
}

/** The Late Fee Descriptions row's own content cell, scoped so a bare amount (no dash, no
 * sibling button) isn't also matched on its ancestor `td`, which shares the same text. */
function lateFeeRowContent(): HTMLElement {
  const label = screen.getByText('Late Fee')
  const row = label.closest('tr')!
  return row.querySelector('.ant-descriptions-item-content') as HTMLElement
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

describe('ProcessReturnPage cancelled receipt (#166)', () => {
  it('shows Receipt Cancelled and no confirm-return button for a CANCELLED receipt', async () => {
    server.use(http.get('*/api/receipts/rcpt-1', () =>
      ok(f.aReceipt({ status: 'CANCELLED', cancellation: f.aReceiptCancellation() }))))

    await renderProcessReturn()

    expect(await screen.findByText('Receipt Cancelled')).toBeInTheDocument()
    expect(screen.getByText(/has been cancelled and cannot be returned/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Confirm & Complete Return/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Calculate Preview' })).not.toBeInTheDocument()
  })
})

describe('ProcessReturnPage late fee decision (#184)', () => {
  function mockPreview(feeRef: { current: number }, bodies: Array<{ applyLateFee?: boolean }>) {
    server.use(http.post('*/api/receipts/rcpt-1/return/preview', async ({ request }) => {
      const body = (await request.json()) as { applyLateFee?: boolean }
      bodies.push(body)
      return ok(previewFor(body, feeRef.current))
    }))
  }

  it('1. does not prompt and shows no late fee row when the calculated fee is 0', async () => {
    // default handler returns calculatedLateFee: 0
    await renderProcessReturn()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Calculate Preview' }))
    await flush()

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.queryByText('Late Fee')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Waive late fee/ })).not.toBeInTheDocument()
  })

  it('2. prompts to apply the late fee when the calculated fee is above 0', async () => {
    const feeRef = { current: 300 }
    mockPreview(feeRef, [])
    await renderProcessReturn()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Calculate Preview' }))

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText(/Do you want to apply the late fee\?/)).toBeInTheDocument()
  })

  it('3. Yes applies the fee: dialog closes, row shows the amount with a waive button, request carries applyLateFee true', async () => {
    const feeRef = { current: 300 }
    const bodies: Array<{ applyLateFee?: boolean }> = []
    mockPreview(feeRef, bodies)
    await renderProcessReturn()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Calculate Preview' }))
    await screen.findByRole('dialog')

    await user.click(screen.getByRole('button', { name: 'Yes' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    expect(screen.getByText(/–\s*₹300/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Waive late fee' })).toBeInTheDocument()
    expect(bodies[bodies.length - 1]?.applyLateFee).toBe(true)
  })

  it('4. No waives the fee: Waived tag and apply button appear, response totals reflected, request carries applyLateFee false', async () => {
    const feeRef = { current: 300 }
    const bodies: Array<{ applyLateFee?: boolean }> = []
    mockPreview(feeRef, bodies)
    await renderProcessReturn()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Calculate Preview' }))
    await screen.findByRole('dialog')

    await user.click(screen.getByRole('button', { name: 'No' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    expect(screen.getByText('Waived')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Apply late fee' })).toBeInTheDocument()
    expect(screen.getByText('REFUND: ₹1,000')).toBeInTheDocument()
    expect(bodies[bodies.length - 1]?.applyLateFee).toBe(false)
  })

  it('5. the flip button re-previews with the opposite decision and no dialog', async () => {
    const feeRef = { current: 300 }
    const bodies: Array<{ applyLateFee?: boolean }> = []
    mockPreview(feeRef, bodies)
    await renderProcessReturn()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Calculate Preview' }))
    await screen.findByRole('dialog')
    await user.click(screen.getByRole('button', { name: 'No' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: 'Apply late fee' }))
    await waitFor(() => expect(bodies[bodies.length - 1]?.applyLateFee).toBe(true))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByText('REFUND: ₹700')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Waive late fee' })).toBeInTheDocument()
  })

  it('6. the waive decision persists across a Notes edit and a fresh Calculate Preview', async () => {
    const feeRef = { current: 300 }
    const bodies: Array<{ applyLateFee?: boolean }> = []
    mockPreview(feeRef, bodies)
    await renderProcessReturn()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Calculate Preview' }))
    await screen.findByRole('dialog')
    await user.click(screen.getByRole('button', { name: 'No' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    await user.type(screen.getByPlaceholderText('Optional general notes'), 'Customer requested waiver')
    await user.click(screen.getByRole('button', { name: 'Calculate Preview' }))
    await waitFor(() => expect(bodies.length).toBe(3))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(bodies[bodies.length - 1]?.applyLateFee).toBe(false)
  })

  it('7. a date/time change that alters the calculated fee re-opens the dialog, including via 0 and back', async () => {
    const feeRef = { current: 300 }
    const bodies: Array<{ applyLateFee?: boolean }> = []
    mockPreview(feeRef, bodies)
    const { container } = await renderProcessReturn()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Calculate Preview' }))
    await screen.findByRole('dialog')
    await user.click(screen.getByRole('button', { name: 'No' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    feeRef.current = 450
    await changeReturnDateTime(user, container, '20 Apr 2026 10:00')
    await user.click(screen.getByRole('button', { name: 'Calculate Preview' }))

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'No' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    feeRef.current = 0
    await changeReturnDateTime(user, container, '19 Apr 2026 10:00')
    await user.click(screen.getByRole('button', { name: 'Calculate Preview' }))
    await waitFor(() => expect(bodies.length).toBeGreaterThanOrEqual(4))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.queryByText('Late Fee')).not.toBeInTheDocument()

    feeRef.current = 300
    await changeReturnDateTime(user, container, '21 Apr 2026 10:00')
    await user.click(screen.getByRole('button', { name: 'Calculate Preview' }))

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
  })

  it('8. Confirm sends the decided flag and is disabled while the dialog is open', async () => {
    const feeRef = { current: 300 }
    const bodies: Array<{ applyLateFee?: boolean }> = []
    mockPreview(feeRef, bodies)
    const confirmBodies: Array<{ applyLateFee?: boolean }> = []
    server.use(http.post('*/api/receipts/rcpt-1/return', async ({ request }) => {
      confirmBodies.push((await request.json()) as { applyLateFee?: boolean })
      return ok(f.anInvoice())
    }))
    await renderProcessReturn()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Calculate Preview' }))
    await screen.findByRole('dialog')

    expect(screen.getByRole('button', { name: /Confirm & Complete Return/i })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'No' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: /Confirm & Complete Return/i }))
    await waitFor(() => expect(confirmBodies).toHaveLength(1))
    expect(confirmBodies[0].applyLateFee).toBe(false)
  })

  it('9. a stale in-flight response for a superseded date is never mistaken for an answer', async () => {
    const feeRef = { current: 300 }
    const bodies: Array<{ applyLateFee?: boolean }> = []
    const held: { resolve: (() => void) | null } = { resolve: null }
    server.use(http.post('*/api/receipts/rcpt-1/return/preview', async ({ request }) => {
      const body = (await request.json()) as { applyLateFee?: boolean }
      bodies.push(body)
      if (bodies.length === 3) {
        await new Promise<void>((resolve) => { held.resolve = resolve })
      }
      return ok(previewFor(body, feeRef.current))
    }))
    const { container } = await renderProcessReturn()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Calculate Preview' }))
    await screen.findByRole('dialog')
    await user.click(screen.getByRole('button', { name: 'No' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByText('Waived')).toBeInTheDocument()

    // Third request (bodies.length will reach 3) is held — fired by Calculate Preview.
    await user.click(screen.getByRole('button', { name: 'Calculate Preview' }))
    await waitFor(() => expect(bodies).toHaveLength(3))

    await changeReturnDateTime(user, container, '22 Apr 2026 10:00')

    // The superseded WAIVE decision must never keep showing a false "Waived" claim, and
    // Confirm must stay gated, while the stale request is still outstanding.
    expect(screen.queryByText('Waived')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Confirm & Complete Return/i })).toBeDisabled()

    held.resolve?.()
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeInTheDocument())
    expect(screen.getByRole('button', { name: /Confirm & Complete Return/i })).toBeDisabled()
    expect(screen.queryByText('Waived')).not.toBeInTheDocument()
    expect(screen.queryByText('Applied')).not.toBeInTheDocument()
  })

  it('10. shows the neutral amount with no tag or flip button between a recalculation and answering', async () => {
    const feeRef = { current: 300 }
    const bodies: Array<{ applyLateFee?: boolean }> = []
    mockPreview(feeRef, bodies)
    const { container } = await renderProcessReturn()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Calculate Preview' }))
    await screen.findByRole('dialog')
    await user.click(screen.getByRole('button', { name: 'Yes' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    feeRef.current = 450
    await changeReturnDateTime(user, container, '20 Apr 2026 10:00')
    await user.click(screen.getByRole('button', { name: 'Calculate Preview' }))
    await screen.findByRole('dialog')

    expect(lateFeeRowContent().textContent).toBe('₹450')
    expect(screen.queryByText('Waived')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Waive late fee|Apply late fee/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Confirm & Complete Return/i })).toBeDisabled()
  })
})

import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { renderWithProviders, flush, screen, within } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import { useAuthStore } from '../../store/authStore'
import CouponsPage from './CouponsPage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })

beforeEach(() => useAuthStore.setState({ token: 'test-token', role: 'OWNER' }))
afterEach(() => useAuthStore.setState({ token: null, role: null }))

describe('CouponsPage', () => {
  it('renders the coupon list', async () => {
    server.use(http.get('*/api/config/coupons', () => ok([
      f.aCoupon({ code: 'SAVE20', discountType: 'PERCENT', value: 20 }),
      f.aCoupon({ id: 'coupon-2', code: 'WELCOME10', discountType: 'FIXED', value: 100, isActive: false }),
    ])))

    renderWithProviders(<CouponsPage />)
    await flush()

    expect(await screen.findByText('SAVE20')).toBeInTheDocument()
    expect(screen.getByText('20%')).toBeInTheDocument()
    expect(screen.getByText('WELCOME10')).toBeInTheDocument()
    expect(screen.getByText('₹100')).toBeInTheDocument()
  })

  it('blocks a PERCENT value above 100 client-side by clamping the InputNumber to its max', async () => {
    server.use(http.get('*/api/config/coupons', () => ok([])))

    const user = userEvent.setup()
    renderWithProviders(<CouponsPage />)
    await flush()

    await user.click(screen.getByRole('button', { name: /Create Coupon/ }))
    const dialog = await screen.findByRole('dialog')

    // Discount type defaults to PERCENT, so the Value field's max={100} is already active.
    const valueInput = within(dialog).getByLabelText('Value (%)') as HTMLInputElement
    await user.clear(valueInput)
    await user.type(valueInput, '200')
    await user.tab() // InputNumber clamps to max on blur, not on every keystroke

    // A percentage discount can never leave this field above 100 — the form has no legal
    // path to a value the backend would reject.
    expect(valueInput.value).toBe('100')
  })

  it('the deactivate toggle calls setStatus with isActive: false', async () => {
    let capturedBody: unknown = null
    server.use(
      http.get('*/api/config/coupons', () => ok([f.aCoupon({ code: 'SAVE20', isActive: true })])),
      http.patch('*/api/config/coupons/:id/status', async ({ request }) => {
        capturedBody = await request.json()
        return ok(f.aCoupon({ code: 'SAVE20', isActive: false }))
      }),
    )

    const user = userEvent.setup()
    renderWithProviders(<CouponsPage />)
    await flush()

    await screen.findByText('SAVE20')
    await user.click(screen.getByRole('button', { name: 'Deactivate' }))

    // Popconfirm renders its own "Deactivate" confirm button alongside the row's trigger —
    // the confirm button is the one added to the DOM after the popup opens.
    const confirmButtons = await screen.findAllByRole('button', { name: 'Deactivate' })
    await user.click(confirmButtons[confirmButtons.length - 1])
    await flush()

    expect(capturedBody).toEqual({ isActive: false })
  })
})

import { describe, it, expect } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { renderWithProviders, flush, screen, waitFor } from '../../test/render'
import { server } from '../../test/server'
import PaymentSettingsPage from './PaymentSettingsPage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })
const upiOff = { upiEnabled: false, upiVpa: null, upiPayeeName: null }

describe('PaymentSettingsPage', () => {
  it('saves a trimmed UPI ID and payee name', async () => {
    let saved: unknown = null
    server.use(
      http.get('*/api/config/payment-settings', () => ok(upiOff)),
      http.put('*/api/config/payment-settings', async ({ request }) => {
        saved = await request.json()
        return ok({ upiEnabled: true, upiVpa: 'shop@ybl', upiPayeeName: 'Manisha Collection' })
      }),
    )
    const user = userEvent.setup()
    renderWithProviders(<PaymentSettingsPage />)
    await flush()

    await user.type(screen.getByLabelText('UPI ID'), ' shop@ybl ')
    await user.type(screen.getByLabelText('Payee name'), 'Manisha Collection')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(saved).toEqual({ upiVpa: 'shop@ybl', upiPayeeName: 'Manisha Collection' }))
  })

  it('rejects a malformed UPI ID without saving', async () => {
    let putCalled = false
    server.use(
      http.get('*/api/config/payment-settings', () => ok(upiOff)),
      http.put('*/api/config/payment-settings', () => { putCalled = true; return ok(upiOff) }),
    )
    const user = userEvent.setup()
    renderWithProviders(<PaymentSettingsPage />)
    await flush()

    await user.type(screen.getByLabelText('UPI ID'), 'not a upi id')
    await user.type(screen.getByLabelText('Payee name'), 'Shop')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Enter a valid UPI ID, e.g. shopname@ybl')).toBeInTheDocument()
    expect(putCalled).toBe(false)
  })

  it('requires a payee name once a UPI ID is entered', async () => {
    server.use(http.get('*/api/config/payment-settings', () => ok(upiOff)))
    const user = userEvent.setup()
    renderWithProviders(<PaymentSettingsPage />)
    await flush()

    await user.type(screen.getByLabelText('UPI ID'), 'shop@ybl')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Payee name is required when a UPI ID is set')).toBeInTheDocument()
  })
})

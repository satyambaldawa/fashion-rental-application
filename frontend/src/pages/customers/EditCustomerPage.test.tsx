import { describe, it, expect, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render } from '@testing-library/react'
import { renderWithProviders, flush, screen } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import EditCustomerPage from './EditCustomerPage'

const renderPage = () =>
  renderWithProviders(<EditCustomerPage />, {
    route: '/customers/cust-1/edit',
    path: '/customers/:id/edit',
  })

// Mounts the real /customers route alongside the edit route so a test can confirm
// Cancel actually navigates there, rather than just not-crashing.
const renderPageWithCustomersRoute = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ConfigProvider>
        <MemoryRouter initialEntries={['/customers/cust-1/edit']}>
          <Routes>
            <Route path="/customers" element={<div>Customers List Page</div>} />
            <Route path="/customers/:id/edit" element={<EditCustomerPage />} />
          </Routes>
        </MemoryRouter>
      </ConfigProvider>
    </QueryClientProvider>,
  )
}

describe('EditCustomerPage', () => {
  it('pre-populates the form from the loaded customer and submits the updated values', async () => {
    const user = userEvent.setup()
    let capturedBody: unknown = null
    server.use(
      http.get('*/api/customers/:id', () =>
        HttpResponse.json({ success: true, data: f.aCustomer({ id: 'cust-1', name: 'Meera', phone: '9811122233', address: 'Pune', customerType: 'MISC', organizationName: null }), error: null })),
      http.put('*/api/customers/:id', async ({ request }) => {
        capturedBody = await request.json()
        return HttpResponse.json({ success: true, data: f.aCustomer({ id: 'cust-1', name: 'Meera Updated' }), error: null })
      }),
    )

    renderPage()
    await flush()

    expect(await screen.findByDisplayValue('Meera')).toBeInTheDocument()
    expect(screen.getByDisplayValue('9811122233')).toBeInTheDocument()

    const nameInput = screen.getByLabelText('Name')
    await user.clear(nameInput)
    await user.type(nameInput, 'Meera Updated')

    await user.click(screen.getByRole('button', { name: 'Save Changes' }))
    await flush()

    expect(capturedBody).toMatchObject({
      name: 'Meera Updated',
      phone: '9811122233',
      customerType: 'MISC',
    })
  })

  it('shows the server error message when the phone conflicts with another customer', async () => {
    const user = userEvent.setup()
    server.use(
      http.get('*/api/customers/:id', () =>
        HttpResponse.json({ success: true, data: f.aCustomer({ id: 'cust-1' }), error: null })),
      http.put('*/api/customers/:id', () =>
        HttpResponse.json(
          { success: false, data: null, error: 'A customer with phone 9811122233 already exists' },
          { status: 409 },
        )),
    )

    renderPage()
    await flush()
    await screen.findByDisplayValue('Meera')

    await user.click(screen.getByRole('button', { name: 'Save Changes' }))
    await flush()

    expect(await screen.findByText('A customer with phone 9811122233 already exists')).toBeInTheDocument()
  })

  it('navigates back to the customers list without saving when Cancel is clicked', async () => {
    const user = userEvent.setup()
    const putHandler = vi.fn()
    server.use(
      http.get('*/api/customers/:id', () =>
        HttpResponse.json({ success: true, data: f.aCustomer({ id: 'cust-1' }), error: null })),
      http.put('*/api/customers/:id', () => {
        putHandler()
        return HttpResponse.json({ success: true, data: f.aCustomer(), error: null })
      }),
    )

    renderPageWithCustomersRoute()
    await flush()
    await screen.findByDisplayValue('Meera')

    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(await screen.findByText('Customers List Page')).toBeInTheDocument()
    expect(putHandler).not.toHaveBeenCalled()
  })

  it('leaves the phone field editable, unlike the read-only phone field on registration', async () => {
    server.use(
      http.get('*/api/customers/:id', () =>
        HttpResponse.json({ success: true, data: f.aCustomer({ id: 'cust-1' }), error: null })),
    )

    renderPage()
    await flush()

    const phoneInput = await screen.findByDisplayValue('9811122233')
    expect(phoneInput).not.toHaveAttribute('readonly')
  })

  it('blocks submission client-side when switching to PROFESSIONAL with no org name', async () => {
    const user = userEvent.setup()
    const putHandler = vi.fn()
    server.use(
      http.get('*/api/customers/:id', () =>
        HttpResponse.json({ success: true, data: f.aCustomer({ id: 'cust-1', customerType: 'MISC', organizationName: null }), error: null })),
      http.put('*/api/customers/:id', () => {
        putHandler()
        return HttpResponse.json({ success: true, data: f.aCustomer(), error: null })
      }),
    )

    renderPage()
    await flush()
    await screen.findByDisplayValue('Meera')

    await user.click(screen.getByRole('radio', { name: 'Professional' }))
    await user.click(screen.getByRole('button', { name: 'Save Changes' }))
    await flush()

    expect(await screen.findByText('Organization name is required for professionals')).toBeInTheDocument()
    expect(putHandler).not.toHaveBeenCalled()
  })
})

import { describe, it, expect, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render } from '@testing-library/react'
import { renderWithProviders, flush, screen, waitFor } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import { STALE_VERSION_MESSAGE } from '../../utils/staleVersion'
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

    await waitFor(() => expect(capturedBody).toMatchObject({
      name: 'Meera Updated',
      phone: '9811122233',
      customerType: 'MISC',
    }))
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

  it('sends the version the customer was loaded with (#189)', async () => {
    const user = userEvent.setup()
    let capturedBody = null as { version?: number } | null
    server.use(
      http.get('*/api/customers/:id', () =>
        HttpResponse.json({ success: true, data: f.aCustomer({ id: 'cust-1', version: 6 }), error: null })),
      http.put('*/api/customers/:id', async ({ request }) => {
        capturedBody = await request.json() as { version?: number }
        return HttpResponse.json({ success: true, data: f.aCustomer({ version: 7 }), error: null })
      }),
    )

    renderPage()
    await flush()
    await screen.findByDisplayValue('Meera')
    await user.click(screen.getByRole('button', { name: 'Save Changes' }))

    await waitFor(() => expect(capturedBody).not.toBeNull())
    expect(capturedBody!.version).toBe(6)
  })

  it('shows the stale message with a Reload button, unlike an ordinary 409 (#189)', async () => {
    const user = userEvent.setup()
    server.use(
      http.get('*/api/customers/:id', () =>
        HttpResponse.json({ success: true, data: f.aCustomer({ id: 'cust-1' }), error: null })),
      http.put('*/api/customers/:id', () =>
        HttpResponse.json({ success: false, data: null, error: STALE_VERSION_MESSAGE }, { status: 409 })),
    )

    renderPage()
    await flush()
    await screen.findByDisplayValue('Meera')
    await user.click(screen.getByRole('button', { name: 'Save Changes' }))

    expect(await screen.findByText(STALE_VERSION_MESSAGE)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reload' })).toBeInTheDocument()
  })

  it('does not offer Reload for a duplicate-phone 409 (#189)', async () => {
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

    expect(await screen.findByText('A customer with phone 9811122233 already exists')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reload' })).not.toBeInTheDocument()
  })

  it('after Reload shows the latest details and the next save sends the new version (#189)', async () => {
    const user = userEvent.setup()
    let loads = 0
    const puts: Array<{ version?: number; name?: string }> = []
    server.use(
      http.get('*/api/customers/:id', () => {
        loads += 1
        const data = loads === 1
          ? f.aCustomer({ id: 'cust-1', name: 'Meera', version: 2 })
          : f.aCustomer({ id: 'cust-1', name: 'Meera Edited Elsewhere', version: 3 })
        return HttpResponse.json({ success: true, data, error: null })
      }),
      http.put('*/api/customers/:id', async ({ request }) => {
        puts.push(await request.json() as { version?: number; name?: string })
        return puts.length === 1
          ? HttpResponse.json({ success: false, data: null, error: STALE_VERSION_MESSAGE }, { status: 409 })
          : HttpResponse.json({ success: true, data: f.aCustomer({ version: 4 }), error: null })
      }),
    )

    renderPage()
    await flush()
    await screen.findByDisplayValue('Meera')
    await user.click(screen.getByRole('button', { name: 'Save Changes' }))
    await user.click(await screen.findByRole('button', { name: 'Reload' }))
    await screen.findByDisplayValue('Meera Edited Elsewhere')
    await user.click(screen.getByRole('button', { name: 'Save Changes' }))

    await waitFor(() => expect(puts).toHaveLength(2))
    expect(puts[0].version).toBe(2)
    expect(puts[1].version).toBe(3)
    expect(puts[1].name).toBe('Meera Edited Elsewhere')
  })

  it('keeps the Reload prompt and shows an error when the reload fails (#189)', async () => {
    const user = userEvent.setup()
    let loads = 0
    server.use(
      http.get('*/api/customers/:id', () => {
        loads += 1
        return loads === 1
          ? HttpResponse.json({ success: true, data: f.aCustomer({ id: 'cust-1' }), error: null })
          : new HttpResponse(null, { status: 500 })
      }),
      http.put('*/api/customers/:id', () =>
        HttpResponse.json({ success: false, data: null, error: STALE_VERSION_MESSAGE }, { status: 409 })),
    )

    renderPage()
    await flush()
    await screen.findByDisplayValue('Meera')
    await user.click(screen.getByRole('button', { name: 'Save Changes' }))
    await user.click(await screen.findByRole('button', { name: 'Reload' }))

    expect(await screen.findByText('Could not reload the customer. Check your connection and try again.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reload' })).toBeInTheDocument()
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

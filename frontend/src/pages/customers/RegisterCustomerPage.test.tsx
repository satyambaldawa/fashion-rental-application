import { describe, it, expect } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render } from '@testing-library/react'
import { flush, screen } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import RegisterCustomerPage from './RegisterCustomerPage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })

function renderPage(initialRoute = '/customers/register') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ConfigProvider>
        <MemoryRouter initialEntries={[initialRoute]}>
          <Routes>
            <Route path="/customers" element={<div>Customers List Page</div>} />
            <Route path="/checkout" element={<div>Checkout Page</div>} />
            <Route path="/customers/register" element={<RegisterCustomerPage />} />
          </Routes>
        </MemoryRouter>
      </ConfigProvider>
    </QueryClientProvider>,
  )
}

async function checkPhone(phone = '9811122233') {
  const user = userEvent.setup()
  await user.type(screen.getByPlaceholderText('e.g. 9876543210'), phone)
  await user.click(screen.getByRole('button', { name: 'Check' }))
  await flush()
  return user
}

/** checkPhone() plus waiting for the async phone-check mutation to land on the
 * registration phase — a fixed flush() alone is not reliably enough time under
 * CI's coverage-instrumented, multi-file-parallel load. */
async function checkPhoneAndReachRegistrationForm(phone = '9811122233') {
  const user = await checkPhone(phone)
  await screen.findByPlaceholderText('Full name')
  return user
}

describe('RegisterCustomerPage', () => {
  it('rejects a phone number that fails the 10-digit Indian mobile pattern', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByPlaceholderText('e.g. 9876543210'), '12345')
    await user.click(screen.getByRole('button', { name: 'Check' }))

    expect(await screen.findByText('Enter a valid 10-digit Indian mobile number')).toBeInTheDocument()
  })

  it('shows the registration form when the phone number is not already registered', async () => {
    server.use(http.get('*/api/customers', () => ok([])))
    renderPage()

    await checkPhoneAndReachRegistrationForm()

    expect(screen.getByRole('heading', { name: 'Register Customer' })).toBeInTheDocument()
    expect(screen.getByText('Phone verified: 9811122233')).toBeInTheDocument()
    expect(screen.getByDisplayValue('9811122233')).toBeInTheDocument()
    // The form's initialValues must actually reach the store — otherwise submitting
    // fails on "Customer type is required" without the user ever touching the radios.
    expect(screen.getByLabelText('Misc')).toBeChecked()
  })

  it('warns and offers a profile link when the phone number already belongs to a customer', async () => {
    server.use(http.get('*/api/customers', () =>
      ok([f.aCustomerSummary({ phone: '9811122233', name: 'Meera' })])))
    renderPage()

    await checkPhone()

    expect(await screen.findByText('Customer already exists: Meera')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'View Profile' })).toBeInTheDocument()
    // Stays on the phone-check phase — the phone input is still editable
    expect(screen.getByPlaceholderText('e.g. 9876543210')).toBeInTheDocument()
  })

  it('navigates to the customer profile list when View Profile is clicked', async () => {
    server.use(http.get('*/api/customers', () =>
      ok([f.aCustomerSummary({ phone: '9811122233', name: 'Meera' })])))
    renderPage()
    const user = await checkPhone()

    await user.click(await screen.findByRole('button', { name: 'View Profile' }))

    expect(screen.getByText('Customers List Page')).toBeInTheDocument()
  })

  it('requires organization name only when customer type is Student or Professional', async () => {
    server.use(http.get('*/api/customers', () => ok([])))
    renderPage()
    const user = await checkPhoneAndReachRegistrationForm()

    expect(screen.queryByLabelText('School Name')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Organization Name')).not.toBeInTheDocument()

    await user.click(screen.getByLabelText('Student'))
    expect(screen.getByLabelText('School Name')).toBeInTheDocument()

    await user.click(screen.getByLabelText('Professional'))
    expect(screen.getByLabelText('Organization Name')).toBeInTheDocument()

    await user.click(screen.getByLabelText('Misc'))
    expect(screen.queryByLabelText('Organization Name')).not.toBeInTheDocument()
  })

  it('registers the customer and shows a success screen', async () => {
    server.use(
      http.get('*/api/customers', () => ok([])),
      http.post('*/api/customers', async ({ request }) => {
        const body = await request.json() as Record<string, unknown>
        expect(body).toMatchObject({ name: 'Meera Joshi', phone: '9811122233', customerType: 'MISC' })
        return ok(f.aCustomer({ name: 'Meera Joshi', phone: '9811122233' }))
      }),
    )
    const user = userEvent.setup()
    renderPage()
    await checkPhoneAndReachRegistrationForm()

    await user.type(screen.getByPlaceholderText('Full name'), 'Meera Joshi')
    await user.click(screen.getByRole('button', { name: 'Register Customer' }))
    await flush()

    expect(await screen.findByRole('heading', { name: 'Customer Registered' })).toBeInTheDocument()
    expect(screen.getByText('Meera Joshi')).toBeInTheDocument()
    expect(screen.getByText('has been registered successfully.')).toBeInTheDocument()
  })

  it('redirects straight to checkout with the new customer id when returnTo=checkout', async () => {
    server.use(
      http.get('*/api/customers', () => ok([])),
      http.post('*/api/customers', () => ok(f.aCustomer({ id: 'cust-99', name: 'Meera Joshi' }))),
    )
    const user = userEvent.setup()
    renderPage('/customers/register?returnTo=checkout')
    await checkPhoneAndReachRegistrationForm()

    await user.type(screen.getByPlaceholderText('Full name'), 'Meera Joshi')
    await user.click(screen.getByRole('button', { name: 'Register Customer' }))
    await flush()

    expect(await screen.findByText('Checkout Page')).toBeInTheDocument()
  })

  it('shows an error alert when registration fails', async () => {
    server.use(
      http.get('*/api/customers', () => ok([])),
      http.post('*/api/customers', () => HttpResponse.json({ success: false, data: null, error: 'boom' }, { status: 500 })),
    )
    const user = userEvent.setup()
    renderPage()
    await checkPhoneAndReachRegistrationForm()

    await user.type(screen.getByPlaceholderText('Full name'), 'Meera Joshi')
    await user.click(screen.getByRole('button', { name: 'Register Customer' }))
    await flush()

    expect(await screen.findByText('Registration failed. Please try again.')).toBeInTheDocument()
  })

  it('resets to the phone-check phase and clears the form when Start Over is clicked', async () => {
    server.use(http.get('*/api/customers', () => ok([])))
    const user = userEvent.setup()
    renderPage()
    await checkPhoneAndReachRegistrationForm()
    await user.type(screen.getByPlaceholderText('Full name'), 'Partial Name')

    await user.click(screen.getByRole('button', { name: 'Start Over' }))

    expect(screen.getByText("Enter the customer's phone number to check if they are already registered.")).toBeInTheDocument()
    expect(screen.getByPlaceholderText('e.g. 9876543210')).toHaveValue('')
  })

  it('lets the owner register a second customer from the success screen', async () => {
    server.use(
      http.get('*/api/customers', () => ok([])),
      http.post('*/api/customers', () => ok(f.aCustomer({ name: 'Meera Joshi' }))),
    )
    const user = userEvent.setup()
    renderPage()
    await checkPhoneAndReachRegistrationForm()
    await user.type(screen.getByPlaceholderText('Full name'), 'Meera Joshi')
    await user.click(screen.getByRole('button', { name: 'Register Customer' }))
    await flush()

    await user.click(await screen.findByRole('button', { name: 'Register Another' }))

    expect(screen.getByText("Enter the customer's phone number to check if they are already registered.")).toBeInTheDocument()
  })

  it('navigates to the customers list from the success screen', async () => {
    server.use(
      http.get('*/api/customers', () => ok([])),
      http.post('*/api/customers', () => ok(f.aCustomer({ name: 'Meera Joshi' }))),
    )
    const user = userEvent.setup()
    renderPage()
    await checkPhoneAndReachRegistrationForm()
    await user.type(screen.getByPlaceholderText('Full name'), 'Meera Joshi')
    await user.click(screen.getByRole('button', { name: 'Register Customer' }))
    await flush()

    await user.click(await screen.findByRole('button', { name: 'Go to Customers' }))

    expect(screen.getByText('Customers List Page')).toBeInTheDocument()
  })
})

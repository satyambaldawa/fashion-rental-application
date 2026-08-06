import { describe, it, expect } from 'vitest'
import userEvent from '@testing-library/user-event'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { flush } from '../../test/render'
import CustomersPage from './CustomersPage'

// Mounts real detail/edit routes (instead of the single-route renderWithProviders helper)
// so a test can tell which one navigation actually landed on.
function renderCustomersPageWithRoutes() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ConfigProvider>
        <MemoryRouter initialEntries={['/customers']}>
          <Routes>
            <Route path="/customers" element={<CustomersPage />} />
            <Route path="/customers/:id" element={<div>Customer Detail Page</div>} />
            <Route path="/customers/:id/edit" element={<div>Edit Customer Page</div>} />
          </Routes>
        </MemoryRouter>
      </ConfigProvider>
    </QueryClientProvider>,
  )
}

describe('CustomersPage', () => {
  it('clicking the edit icon navigates to the edit page without falling through to the card click handler', async () => {
    const user = userEvent.setup()
    renderCustomersPageWithRoutes()

    await user.type(
      screen.getByPlaceholderText('Search by name or phone (min 3 characters)'),
      '981',
    )
    await flush(350)

    await user.click(await screen.findByRole('img', { name: 'edit' }))

    expect(await screen.findByText('Edit Customer Page')).toBeInTheDocument()
    expect(screen.queryByText('Customer Detail Page')).not.toBeInTheDocument()
  })

  it('clicking the card elsewhere navigates to the customer detail page', async () => {
    const user = userEvent.setup()
    renderCustomersPageWithRoutes()

    await user.type(
      screen.getByPlaceholderText('Search by name or phone (min 3 characters)'),
      '981',
    )
    await flush(350)

    await user.click(await screen.findByText('Meera'))

    expect(await screen.findByText('Customer Detail Page')).toBeInTheDocument()
  })
})

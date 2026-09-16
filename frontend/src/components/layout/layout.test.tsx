import { describe, it, expect, beforeEach } from 'vitest'
import { renderWithProviders, flush } from '../../test/render'
import { useAuthStore } from '../../store/authStore'
import AppLayout from './AppLayout'

describe('AppLayout', () => {
  beforeEach(() => useAuthStore.setState({ token: 'test-token', role: 'OWNER' }))

  it('renders the app shell and sidebar on the checkout route', async () => {
    const { container } = renderWithProviders(<AppLayout />, { route: '/checkout' })
    await flush()
    expect(container.firstChild).toBeTruthy()
  })

  it('renders the inventory route inside the shell', async () => {
    const { container } = renderWithProviders(<AppLayout />, { route: '/inventory' })
    await flush()
    expect(container.firstChild).toBeTruthy()
  })

  it('renders the reports route inside the shell', async () => {
    const { container } = renderWithProviders(<AppLayout />, { route: '/reports' })
    await flush()
    expect(container.firstChild).toBeTruthy()
  })
})

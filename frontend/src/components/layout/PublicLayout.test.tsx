import { describe, it, expect } from 'vitest'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import { flush } from '../../test/render'
import { useAuthStore } from '../../store/authStore'
import PublicLayout from './PublicLayout'
import GalleryPage from '../../pages/public/GalleryPage'

// Mounts the real /login route alongside /gallery so a test can confirm a nav
// click actually navigates there, rather than just not-crashing.
const renderGalleryRoute = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ConfigProvider>
        <MemoryRouter initialEntries={['/gallery']}>
          <Routes>
            <Route path="/login" element={<div>Login Page</div>} />
            <Route path="/gallery" element={<PublicLayout><GalleryPage /></PublicLayout>} />
          </Routes>
        </MemoryRouter>
      </ConfigProvider>
    </QueryClientProvider>,
  )
}

describe('PublicLayout', () => {
  describe('logged out', () => {
    it('shows exactly Gallery, Login, and New Rental in the nav, with no Logout control', async () => {
      useAuthStore.setState({ token: null, role: null })

      renderGalleryRoute()
      await flush()

      expect(screen.getByRole('menuitem', { name: 'Gallery' })).toBeInTheDocument()
      expect(screen.getByRole('menuitem', { name: 'Login' })).toBeInTheDocument()
      expect(screen.getByRole('menuitem', { name: 'New Rental' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /logout/i })).not.toBeInTheDocument()
    })

    it('navigates to /login when Login is clicked', async () => {
      useAuthStore.setState({ token: null, role: null })
      const user = userEvent.setup()

      renderGalleryRoute()
      await flush()

      await user.click(screen.getByRole('menuitem', { name: 'Login' }))

      expect(await screen.findByText('Login Page')).toBeInTheDocument()
    })

    it('navigates to /login when New Rental is clicked', async () => {
      useAuthStore.setState({ token: null, role: null })
      const user = userEvent.setup()

      renderGalleryRoute()
      await flush()

      await user.click(screen.getByRole('menuitem', { name: 'New Rental' }))

      expect(await screen.findByText('Login Page')).toBeInTheDocument()
    })

    it('renders the gallery content directly, without redirecting to /login', async () => {
      useAuthStore.setState({ token: null, role: null })

      renderGalleryRoute()
      await flush()

      expect(screen.getByText('Royal Sherwani')).toBeInTheDocument()
      expect(screen.queryByText('Login Page')).not.toBeInTheDocument()
    })
  })

  describe('logged in', () => {
    it('shows the staff nav — including a Gallery tab — plus a Logout control', async () => {
      useAuthStore.setState({ token: 'test-token', role: 'OWNER' })

      renderGalleryRoute()
      await flush()

      expect(screen.getByRole('menuitem', { name: 'New Rental' })).toBeInTheDocument()
      expect(screen.getByRole('menuitem', { name: 'Active Rentals' })).toBeInTheDocument()
      expect(screen.getByRole('menuitem', { name: 'Gallery' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /logout/i })).toBeInTheDocument()
      expect(screen.queryByRole('menuitem', { name: 'Login' })).not.toBeInTheDocument()
    })
  })
})

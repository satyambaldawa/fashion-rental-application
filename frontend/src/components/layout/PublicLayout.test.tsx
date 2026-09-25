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
import AboutPage from '../../pages/public/AboutPage'
import LoginPage from '../../pages/LoginPage'

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

const renderAboutRoute = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ConfigProvider>
        <MemoryRouter initialEntries={['/about']}>
          <Routes>
            <Route path="/login" element={<div>Login Page</div>} />
            <Route path="/about" element={<PublicLayout><AboutPage /></PublicLayout>} />
          </Routes>
        </MemoryRouter>
      </ConfigProvider>
    </QueryClientProvider>,
  )
}

const renderLoginRoute = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ConfigProvider>
        <MemoryRouter initialEntries={['/login']}>
          <Routes>
            <Route path="/login" element={<PublicLayout><LoginPage /></PublicLayout>} />
          </Routes>
        </MemoryRouter>
      </ConfigProvider>
    </QueryClientProvider>,
  )
}

describe('PublicLayout', () => {
  describe('logged out', () => {
    it('shows exactly Gallery, Reviews and About in the nav, plus a Login button in the corner, with no Logout control', async () => {
      useAuthStore.setState({ token: null, role: null })

      renderGalleryRoute()
      await flush()

      expect(screen.getByRole('button', { name: 'Gallery' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Reviews' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'About' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /login/i })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'New Rental' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /logout/i })).not.toBeInTheDocument()
    })

    it('navigates to /login when Login is clicked', async () => {
      useAuthStore.setState({ token: null, role: null })
      const user = userEvent.setup()

      renderGalleryRoute()
      await flush()

      await user.click(screen.getByRole('button', { name: /login/i }))

      expect(await screen.findByText('Login Page')).toBeInTheDocument()
    })

    it('renders the gallery content directly, without redirecting to /login', async () => {
      useAuthStore.setState({ token: null, role: null })

      renderGalleryRoute()
      await flush()

      expect(screen.getByText('Royal Sherwani')).toBeInTheDocument()
      expect(screen.queryByText('Login Page')).not.toBeInTheDocument()
    })

    // A visitor lands on /login either by navigating there directly or after
    // clicking Logout — either way they should still be able to reach the
    // public pages (or sign back in) without hitting a dead end.
    it('still shows the public nav on the login page itself', async () => {
      useAuthStore.setState({ token: null, role: null })

      renderLoginRoute()
      await flush()

      expect(screen.getByRole('button', { name: 'Gallery' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Reviews' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'About' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /login/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Sign In' })).toBeInTheDocument()
    })

    it('renders the About content directly at /about, shows the public nav, and never reaches the login page', async () => {
      useAuthStore.setState({ token: null, role: null })

      renderAboutRoute()
      await flush()

      expect(screen.getByRole('heading', { name: /about/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Gallery' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Reviews' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'About' })).toBeInTheDocument()
      expect(screen.queryByText('Login Page')).not.toBeInTheDocument()
    })
  })

  describe('logged in', () => {
    it('shows the staff nav — including a Gallery tab — plus a Logout control', async () => {
      useAuthStore.setState({ token: 'test-token', role: 'OWNER' })

      renderGalleryRoute()
      await flush()

      expect(screen.getByRole('button', { name: 'New Rental' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Active Rentals' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Gallery' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /logout/i })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /login/i })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'About' })).not.toBeInTheDocument()
    })
  })
})

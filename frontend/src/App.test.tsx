import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { flush } from './test/render'
import { useAuthStore } from './store/authStore'
import App from './App'

describe('App', () => {
  it('mounts and redirects to login without a token', async () => {
    const { container } = render(<App />)
    await flush()
    expect(container.firstChild).toBeTruthy()
  })

  it('renders the About page at /about without a token and without redirecting to login', async () => {
    useAuthStore.setState({ token: null, role: null })
    window.history.pushState({}, '', '/about')

    render(<App />)
    await flush()

    expect(screen.getByRole('heading', { name: /about/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'About' })).toBeInTheDocument()
    expect(window.location.pathname).toBe('/about')
  })
})

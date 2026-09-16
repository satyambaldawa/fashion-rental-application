import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { renderWithProviders, flush, screen } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import { useAuthStore } from '../../store/authStore'
import AppLayout from './AppLayout'
import type { Cart, CatalogueCartItem } from '../../types/receipt'

function jwtWithRole(role: string): string {
  const encode = (obj: object) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${encode({ alg: 'HS256' })}.${encode({ sub: 'user', role, exp: 9999999999 })}.signature`
}

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

describe('AppLayout quick rental nav', () => {
  const CART_STORAGE_KEY = 'rental_cart_v2'

  afterEach(() => {
    localStorage.removeItem(CART_STORAGE_KEY)
    useAuthStore.setState({ token: null, role: null })
  })

  it('shows Quick Rental in the nav for an owner', async () => {
    useAuthStore.setState({ token: jwtWithRole('OWNER'), role: 'OWNER' })
    renderWithProviders(<AppLayout />, { route: '/checkout' })
    await flush()
    expect(screen.getByText('Quick Rental')).toBeInTheDocument()
  })

  it('shows Quick Rental in the nav for an executive', async () => {
    useAuthStore.setState({ token: jwtWithRole('EXECUTIVE'), role: 'EXECUTIVE' })
    renderWithProviders(<AppLayout />, { route: '/checkout' })
    await flush()
    expect(screen.getByText('Quick Rental')).toBeInTheDocument()
  })

  it('navigating from checkout to quick rental opens the typed-in screen, not whatever screen checkout was on', async () => {
    useAuthStore.setState({ token: jwtWithRole('OWNER'), role: 'OWNER' })

    const item: CatalogueCartItem = {
      kind: 'CATALOGUE', lineKey: 'item-1', itemId: 'item-1', itemName: 'Royal Sherwani',
      itemType: 'INDIVIDUAL', category: 'COSTUME', size: 'M', componentNames: null,
      thumbnailUrl: null, rate: 300, deposit: 1000, quantity: 1, availableQuantity: 3,
    }
    const cart: Cart = {
      startDatetime: '2026-04-18T10:00:00+05:30', endDatetime: '2026-04-19T10:00:00+05:30',
      rentalDays: 1, items: [item],
    }
    localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(cart))
    server.use(
      http.get('*/api/items', () => HttpResponse.json({
        success: true, error: null,
        data: { content: [f.anItemSummary({ id: 'item-1' })], totalElements: 1, totalPages: 1, number: 0, size: 20 },
      })),
    )

    const user = userEvent.setup()
    renderWithProviders(<AppLayout />, { route: '/checkout' })
    await flush()

    // Without distinct `key` props on the two <CheckoutPage> routes, React would reuse the
    // mounted instance and its internal `screen` state across this navigation -- so /quick-rental
    // would open on whatever screen /checkout was last showing (here: preview) instead of
    // its own typed-in entry screen.
    await user.click(await screen.findByRole('button', { name: 'Checkout' }))
    await flush()
    expect(await screen.findByRole('button', { name: 'Confirm & Proceed' })).toBeInTheDocument()

    await user.click(screen.getByText('Quick Rental'))
    await flush()

    expect(screen.queryByRole('button', { name: 'Confirm & Proceed' })).not.toBeInTheDocument()
    expect(await screen.findByRole('button', { name: /browse inventory/i })).toBeInTheDocument()
  })
})

import { describe, it, expect, afterEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { renderWithProviders, flush, screen, within } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import CheckoutPage from './CheckoutPage'
import type { Cart, CartItem } from '../../types/receipt'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })
const page = (content: unknown[]) => ({
  content, totalElements: content.length, totalPages: 1, number: 0, size: 20,
})

const CART_STORAGE_KEY = 'rental_cart'

const baseCartItem: CartItem = {
  itemId: 'item-1',
  itemName: 'Royal Sherwani',
  itemType: 'INDIVIDUAL',
  category: 'COSTUME',
  size: 'M',
  componentNames: null,
  thumbnailUrl: null,
  rate: 300,
  deposit: 1000,
  quantity: 1,
  availableQuantity: 3,
}

function seedCart(items: CartItem[]) {
  const cart: Cart = {
    startDatetime: '2026-04-18T10:00:00+05:30',
    endDatetime: '2026-04-19T10:00:00+05:30',
    rentalDays: 1,
    items,
  }
  localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(cart))
}

// CheckoutPage reads the cart from localStorage on mount, so the fixture must be
// seeded before renderWithProviders is called. The preview table (and its thumbnail
// fallback logic) only mounts once the user advances past the browse screen.
async function goToPreview() {
  const user = userEvent.setup()
  renderWithProviders(<CheckoutPage />)
  await flush()
  await user.click(await screen.findByRole('button', { name: 'Checkout' }))
  await flush()
}

describe('CheckoutPage preview thumbnails', () => {
  afterEach(() => {
    localStorage.removeItem(CART_STORAGE_KEY)
  })

  it('renders the img from the cart item thumbnailUrl when present', async () => {
    seedCart([{ ...baseCartItem, thumbnailUrl: 'https://r2.example/cart-thumb.jpg' }])
    server.use(
      http.get('*/api/items', () => ok(page([
        f.anItemSummary({ id: 'item-1', thumbnailUrl: 'https://r2.example/fresh-thumb.jpg' }),
      ]))),
    )

    await goToPreview()

    const img = await screen.findByRole('img', { name: 'Royal Sherwani' })
    expect(img).toHaveAttribute('src', 'https://r2.example/cart-thumb.jpg')
  })

  it('falls back to the freshly fetched item thumbnail when a stale cart entry has none', async () => {
    seedCart([{ ...baseCartItem, thumbnailUrl: null }])
    server.use(
      http.get('*/api/items', () => ok(page([
        f.anItemSummary({ id: 'item-1', thumbnailUrl: 'https://r2.example/fresh-thumb.jpg' }),
      ]))),
    )

    await goToPreview()

    const img = await screen.findByRole('img', { name: 'Royal Sherwani' })
    expect(img).toHaveAttribute('src', 'https://r2.example/fresh-thumb.jpg')
  })

  it('renders the placeholder when neither the cart item nor the fresh item has a thumbnail', async () => {
    seedCart([{ ...baseCartItem, thumbnailUrl: null }])
    server.use(
      http.get('*/api/items', () => ok(page([
        f.anItemSummary({ id: 'item-1', thumbnailUrl: null }),
      ]))),
    )

    await goToPreview()

    const nameCell = await screen.findByText('Royal Sherwani')
    const itemCell = nameCell.closest('td')!
    expect(within(itemCell).queryByRole('img')).not.toBeInTheDocument()
    expect(itemCell.querySelector('svg')).toBeInTheDocument()
  })
})

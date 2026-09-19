import { describe, it, expect, vi, afterEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { renderWithProviders, flush, screen } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import PackageDetailModal from './PackageDetailModal'
import type { ItemSummary, PackageComponent } from '../../types/inventory'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })

const packageSummary: ItemSummary = {
  ...f.anItemSummary(),
  id: 'pkg-1', name: 'Groom Wedding Set', itemType: 'PACKAGE',
  availableQuantity: 2, componentNames: ['Sherwani ×1', 'Pagdi ×1'],
}

const component: PackageComponent = {
  componentItemId: 'comp-1', componentItemName: 'Golden Sherwani', componentItemCategory: 'COSTUME',
  componentItemSize: 'L', componentItemDescription: 'Richly embroidered sherwani',
  componentItemPhotos: [], quantity: 1,
}

describe('PackageDetailModal', () => {
  afterEach(() => server.resetHandlers())

  it('renders nothing when no item is selected', () => {
    renderWithProviders(
      <PackageDetailModal item={null} onClose={() => {}} onAddToCart={() => {}} inCartQty={0} />,
    )

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('shows package price, deposit and component list once loaded', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({
      id: 'pkg-1', itemType: 'PACKAGE', components: [component],
    }))))

    renderWithProviders(
      <PackageDetailModal item={packageSummary} onClose={() => {}} onAddToCart={() => {}} inCartQty={0} />,
    )
    await flush()

    expect(screen.getByText('Groom Wedding Set')).toBeInTheDocument()
    expect(screen.getByText('₹300')).toBeInTheDocument()
    expect(screen.getByText(/Deposit: ₹1,000/)).toBeInTheDocument()
    expect(screen.getByText('Includes (1 item)')).toBeInTheDocument()
    expect(screen.getByText('Golden Sherwani')).toBeInTheDocument()
    expect(screen.getByText('Richly embroidered sherwani')).toBeInTheDocument()
  })

  it('shows available quantity as a tag when in stock', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'pkg-1', itemType: 'PACKAGE', components: [] }))))

    renderWithProviders(
      <PackageDetailModal item={packageSummary} onClose={() => {}} onAddToCart={() => {}} inCartQty={0} />,
    )
    await flush()

    expect(screen.getByText('2 sets available')).toBeInTheDocument()
  })

  it('shows unavailable tag and hides add-to-cart button when out of stock', async () => {
    const outOfStock: ItemSummary = { ...packageSummary, availableQuantity: 0 }
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'pkg-1', itemType: 'PACKAGE', components: [] }))))

    renderWithProviders(
      <PackageDetailModal item={outOfStock} onClose={() => {}} onAddToCart={() => {}} inCartQty={0} />,
    )
    await flush()

    expect(screen.getByText('Unavailable')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /add to cart/i })).not.toBeInTheDocument()
  })

  it('shows the in-cart quantity badge when the package is already in the cart', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'pkg-1', itemType: 'PACKAGE', components: [] }))))

    renderWithProviders(
      <PackageDetailModal item={packageSummary} onClose={() => {}} onAddToCart={() => {}} inCartQty={2} />,
    )
    await flush()

    expect(screen.getByText('In cart ×2')).toBeInTheDocument()
  })

  it('calls onAddToCart and onClose when Add to Cart is clicked', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'pkg-1', itemType: 'PACKAGE', components: [component] }))))
    const onAddToCart = vi.fn()
    const onClose = vi.fn()
    const user = userEvent.setup()

    renderWithProviders(
      <PackageDetailModal item={packageSummary} onClose={onClose} onAddToCart={onAddToCart} inCartQty={0} />,
    )
    await flush()

    await user.click(screen.getByRole('button', { name: /add to cart/i }))

    expect(onAddToCart).toHaveBeenCalledWith(packageSummary)
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('calls onClose when the modal is cancelled', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'pkg-1', itemType: 'PACKAGE', components: [] }))))
    const onClose = vi.fn()

    renderWithProviders(
      <PackageDetailModal item={packageSummary} onClose={onClose} onAddToCart={() => {}} inCartQty={0} />,
    )
    await flush()

    const closeButton = document.querySelector('.ant-modal-close') as HTMLElement
    await userEvent.click(closeButton)

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('falls back to a placeholder icon when a component has no photos', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'pkg-1', itemType: 'PACKAGE', components: [component] }))))

    const { container } = renderWithProviders(
      <PackageDetailModal item={packageSummary} onClose={() => {}} onAddToCart={() => {}} inCartQty={0} />,
    )
    await flush()

    expect(container.querySelector('img')).not.toBeInTheDocument()
    expect(screen.getByText('Golden Sherwani')).toBeInTheDocument()
  })

  it('renders a single component photo directly without a carousel', async () => {
    const withPhoto: PackageComponent = {
      ...component,
      componentItemPhotos: [{ id: 'p1', url: 'https://cdn.example.com/comp.jpg', thumbnailUrl: 'https://cdn.example.com/comp-thumb.jpg', sortOrder: 0 }],
    }
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'pkg-1', itemType: 'PACKAGE', components: [withPhoto] }))))

    renderWithProviders(
      <PackageDetailModal item={packageSummary} onClose={() => {}} onAddToCart={() => {}} inCartQty={0} />,
    )
    await flush()

    const img = screen.getByAltText('Golden Sherwani')
    expect(img).toHaveAttribute('src', 'https://cdn.example.com/comp.jpg')
  })
})

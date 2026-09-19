import { describe, it, expect, vi, afterEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import { renderWithProviders, flush, screen, render } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import ItemBrowseModal from './ItemBrowseModal'
import type { ItemSummary, PackageComponent } from '../../types/inventory'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })

const individualSummary: ItemSummary = {
  ...f.anItemSummary(), id: 'item-1', name: 'Royal Sherwani', availableQuantity: 3,
}

const packageSummary: ItemSummary = {
  ...f.anItemSummary(), id: 'pkg-1', name: 'Groom Wedding Set', itemType: 'PACKAGE', availableQuantity: 2,
}

const component: PackageComponent = {
  componentItemId: 'comp-1', componentItemName: 'Golden Sherwani', componentItemCategory: 'COSTUME',
  componentItemSize: 'L', componentItemDescription: 'Richly embroidered',
  componentItemPhotos: [{ id: 'cp1', url: 'https://cdn.example.com/comp.jpg', thumbnailUrl: 'https://cdn.example.com/comp-thumb.jpg', sortOrder: 0 }],
  quantity: 1,
}

function noop() {}

describe('ItemBrowseModal', () => {
  afterEach(() => server.resetHandlers())

  it('renders nothing when no item is selected', () => {
    renderWithProviders(
      <ItemBrowseModal item={null} onClose={noop} onAddToCart={noop} onRemoveFromCart={noop}
        onUpdateQty={noop} inCartQty={0} inCartLineKey={null} maxQty={5} />,
    )

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('shows a placeholder icon when the item has no photos', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'item-1', photos: [] }))))

    renderWithProviders(
      <ItemBrowseModal item={individualSummary} onClose={noop} onAddToCart={noop} onRemoveFromCart={noop}
        onUpdateQty={noop} inCartQty={0} inCartLineKey={null} maxQty={5} />,
    )
    await flush()

    expect(document.querySelector('img')).not.toBeInTheDocument()
  })

  it('shows rate, deposit and availability for an individual item', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'item-1', photos: [] }))))

    renderWithProviders(
      <ItemBrowseModal item={individualSummary} onClose={noop} onAddToCart={noop} onRemoveFromCart={noop}
        onUpdateQty={noop} inCartQty={0} inCartLineKey={null} maxQty={5} />,
    )
    await flush()

    expect(screen.getByText('₹300')).toBeInTheDocument()
    expect(screen.getByText('₹1,000')).toBeInTheDocument()
    expect(screen.getByText('3 available')).toBeInTheDocument()
    expect(screen.getByText('Individual')).toBeInTheDocument()
  })

  it('shows Unavailable and hides Add to Cart when out of stock', async () => {
    const outOfStock: ItemSummary = { ...individualSummary, availableQuantity: 0 }
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'item-1', photos: [] }))))

    renderWithProviders(
      <ItemBrowseModal item={outOfStock} onClose={noop} onAddToCart={noop} onRemoveFromCart={noop}
        onUpdateQty={noop} inCartQty={0} inCartLineKey={null} maxQty={5} />,
    )
    await flush()

    expect(screen.getByText('Unavailable')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /add to cart/i })).not.toBeInTheDocument()
  })

  it('adds to cart and closes when Add to Cart is clicked', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'item-1', photos: [] }))))
    const onAddToCart = vi.fn()
    const onClose = vi.fn()

    renderWithProviders(
      <ItemBrowseModal item={individualSummary} onClose={onClose} onAddToCart={onAddToCart} onRemoveFromCart={noop}
        onUpdateQty={noop} inCartQty={0} inCartLineKey={null} maxQty={5} />,
    )
    await flush()

    await userEvent.click(screen.getByRole('button', { name: /add to cart/i }))

    expect(onAddToCart).toHaveBeenCalledWith(individualSummary)
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('shows a stepper with the current quantity when already in the cart', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'item-1', photos: [] }))))

    renderWithProviders(
      <ItemBrowseModal item={individualSummary} onClose={noop} onAddToCart={noop} onRemoveFromCart={noop}
        onUpdateQty={noop} inCartQty={2} inCartLineKey="item-1" maxQty={5} />,
    )
    await flush()

    expect(screen.getByText('2')).toBeInTheDocument()
    expect(screen.getByText('in cart')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /add to cart/i })).not.toBeInTheDocument()
  })

  it('increments quantity via onUpdateQty when plus is clicked below maxQty', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'item-1', photos: [] }))))
    const onUpdateQty = vi.fn()

    const { container } = renderWithProviders(
      <ItemBrowseModal item={individualSummary} onClose={noop} onAddToCart={noop} onRemoveFromCart={noop}
        onUpdateQty={onUpdateQty} inCartQty={2} inCartLineKey="item-1" maxQty={5} />,
    )
    await flush()

    const plusButton = container.ownerDocument.querySelector('.anticon-plus')!.closest('button')!
    await userEvent.click(plusButton)

    expect(onUpdateQty).toHaveBeenCalledWith('item-1', 3)
  })

  it('disables the plus button once maxQty is reached', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'item-1', photos: [] }))))

    const { container } = renderWithProviders(
      <ItemBrowseModal item={individualSummary} onClose={noop} onAddToCart={noop} onRemoveFromCart={noop}
        onUpdateQty={noop} inCartQty={5} inCartLineKey="item-1" maxQty={5} />,
    )
    await flush()

    const plusButton = container.ownerDocument.querySelector('.anticon-plus')!.closest('button')!
    expect(plusButton).toBeDisabled()
  })

  it('removes from cart when minus is clicked at quantity 1', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'item-1', photos: [] }))))
    const onRemoveFromCart = vi.fn()
    const onUpdateQty = vi.fn()

    const { container } = renderWithProviders(
      <ItemBrowseModal item={individualSummary} onClose={noop} onAddToCart={noop} onRemoveFromCart={onRemoveFromCart}
        onUpdateQty={onUpdateQty} inCartQty={1} inCartLineKey="item-1" maxQty={5} />,
    )
    await flush()

    const minusButton = container.ownerDocument.querySelector('.anticon-minus')!.closest('button')!
    await userEvent.click(minusButton)

    expect(onRemoveFromCart).toHaveBeenCalledWith('item-1')
    expect(onUpdateQty).not.toHaveBeenCalled()
  })

  it('decrements via onUpdateQty when minus is clicked above quantity 1', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'item-1', photos: [] }))))
    const onUpdateQty = vi.fn()

    const { container } = renderWithProviders(
      <ItemBrowseModal item={individualSummary} onClose={noop} onAddToCart={noop} onRemoveFromCart={noop}
        onUpdateQty={onUpdateQty} inCartQty={3} inCartLineKey="item-1" maxQty={5} />,
    )
    await flush()

    const minusButton = container.ownerDocument.querySelector('.anticon-minus')!.closest('button')!
    await userEvent.click(minusButton)

    expect(onUpdateQty).toHaveBeenCalledWith('item-1', 2)
  })

  it('shows the combo tag and component list for a package', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({
      id: 'pkg-1', itemType: 'PACKAGE', components: [component], photos: [],
    }))))

    renderWithProviders(
      <ItemBrowseModal item={packageSummary} onClose={noop} onAddToCart={noop} onRemoveFromCart={noop}
        onUpdateQty={noop} inCartQty={0} inCartLineKey={null} maxQty={5} />,
    )
    await flush()

    expect(screen.getByText('Combo')).toBeInTheDocument()
    expect(screen.getByText('Includes (1 item)')).toBeInTheDocument()
    expect(screen.getAllByText('Golden Sherwani').length).toBeGreaterThan(0)
    expect(screen.getByText('Richly embroidered')).toBeInTheDocument()
    expect(screen.getByText('×1 per set')).toBeInTheDocument()
  })

  it('shows a placeholder for a component with no photos', async () => {
    const noPhotoComponent: PackageComponent = { ...component, componentItemPhotos: [] }
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({
      id: 'pkg-1', itemType: 'PACKAGE', components: [noPhotoComponent], photos: [],
    }))))

    renderWithProviders(
      <ItemBrowseModal item={packageSummary} onClose={noop} onAddToCart={noop} onRemoveFromCart={noop}
        onUpdateQty={noop} inCartQty={0} inCartLineKey={null} maxQty={5} />,
    )
    await flush()

    expect(screen.getByText('Golden Sherwani')).toBeInTheDocument()
    expect(screen.queryByAltText('Golden Sherwani')).not.toBeInTheDocument()
  })

  it('cycles the photo carousel forward and backward across package + component photos', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({
      id: 'pkg-1', name: 'Groom Wedding Set', itemType: 'PACKAGE', components: [component],
      photos: [{ id: 'p1', url: 'https://cdn.example.com/pkg.jpg', thumbnailUrl: 'https://cdn.example.com/pkg-thumb.jpg', sortOrder: 0 }],
    }))))

    renderWithProviders(
      <ItemBrowseModal item={packageSummary} onClose={noop} onAddToCart={noop} onRemoveFromCart={noop}
        onUpdateQty={noop} inCartQty={0} inCartLineKey={null} maxQty={5} />,
    )
    await flush()

    // 2 slides total: 1 package photo + 1 component photo
    expect(screen.getByText('1 / 2')).toBeInTheDocument()
    expect(screen.getByText('Groom Wedding Set (combo)')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /right/i }))
    expect(screen.getByText('2 / 2')).toBeInTheDocument()
    expect(screen.getAllByText('Golden Sherwani').length).toBeGreaterThan(0)

    // wraps back to the first slide
    await userEvent.click(screen.getByRole('button', { name: /right/i }))
    expect(screen.getByText('1 / 2')).toBeInTheDocument()

    // left arrow wraps backward to the last slide
    await userEvent.click(screen.getByRole('button', { name: /left/i }))
    expect(screen.getByText('2 / 2')).toBeInTheDocument()
  })

  it('resets the photo index when a different item is opened', async () => {
    server.use(http.get('*/api/items/:id', ({ params }) => {
      if (params.id === 'pkg-1') {
        return ok(f.anItemDetail({
          id: 'pkg-1', itemType: 'PACKAGE', components: [],
          photos: [
            { id: 'p1', url: 'https://cdn.example.com/pkg-1.jpg', thumbnailUrl: 't', sortOrder: 0 },
            { id: 'p2', url: 'https://cdn.example.com/pkg-2.jpg', thumbnailUrl: 't', sortOrder: 1 },
          ],
        }))
      }
      return ok(f.anItemDetail({ id: 'item-1', photos: [] }))
    }))

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
    const wrap = (selected: ItemSummary | null) => (
      <QueryClientProvider client={queryClient}>
        <ConfigProvider>
          <ItemBrowseModal item={selected} onClose={noop} onAddToCart={noop} onRemoveFromCart={noop}
            onUpdateQty={noop} inCartQty={0} inCartLineKey={null} maxQty={5} />
        </ConfigProvider>
      </QueryClientProvider>
    )

    const { rerender } = render(wrap(packageSummary))
    await flush()
    await userEvent.click(screen.getByRole('button', { name: /right/i }))
    expect(screen.getByText('2 / 2')).toBeInTheDocument()

    rerender(wrap(individualSummary))
    await flush()

    expect(document.querySelector('img')).not.toBeInTheDocument()
  })
})

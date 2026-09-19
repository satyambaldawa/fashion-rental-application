import { describe, it, expect, vi, afterEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render } from '@testing-library/react'
import { renderWithProviders, flush, screen, within } from '../../../test/render'
import { server } from '../../../test/server'
import * as f from '../../../test/factories'
import ItemDetailDrawer from './ItemDetailDrawer'
import type { PackageComponent } from '../../../types/inventory'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })

function noop() {}

describe('ItemDetailDrawer', () => {
  afterEach(() => server.resetHandlers())

  it('renders nothing when no item is selected', () => {
    renderWithProviders(<ItemDetailDrawer itemId={null} onClose={noop} />)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('shows core item details once loaded', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({
      id: 'item-1', name: 'Royal Sherwani', category: 'COSTUME', rate: 300, deposit: 1000,
      quantity: 4, description: 'A fine sherwani', notes: 'Handle with care', photos: [],
    }))))

    renderWithProviders(<ItemDetailDrawer itemId="item-1" onClose={noop} />)
    await flush()

    expect(screen.getByText('₹300')).toBeInTheDocument()
    expect(screen.getByText('₹1,000')).toBeInTheDocument()
    expect(screen.getByText('4')).toBeInTheDocument()
    expect(screen.getByText('A fine sherwani')).toBeInTheDocument()
    expect(screen.getByText('Handle with care')).toBeInTheDocument()
    expect(screen.getByText('COSTUME')).toBeInTheDocument()
  })

  it('shows the Package tag and component breakdown for a package item', async () => {
    const component: PackageComponent = {
      componentItemId: 'comp-1', componentItemName: 'Golden Sherwani', componentItemCategory: 'COSTUME',
      componentItemSize: 'L', componentItemDescription: null, componentItemPhotos: [], quantity: 2,
    }
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({
      id: 'pkg-1', itemType: 'PACKAGE', components: [component], photos: [],
    }))))

    renderWithProviders(<ItemDetailDrawer itemId="pkg-1" onClose={noop} />)
    await flush()

    expect(screen.getByText('Package')).toBeInTheDocument()
    expect(screen.getByText('Includes')).toBeInTheDocument()
    expect(screen.getByText('Golden Sherwani')).toBeInTheDocument()
    expect(screen.getByText('×2')).toBeInTheDocument()
  })

  it('does not show the Includes section for an individual item with no components', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'item-1', components: null, photos: [] }))))

    renderWithProviders(<ItemDetailDrawer itemId="item-1" onClose={noop} />)
    await flush()

    expect(screen.queryByText('Includes')).not.toBeInTheDocument()
  })

  it('clones the item and closes the drawer', async () => {
    server.use(
      http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'item-1', photos: [] }))),
      http.post('*/api/items/:id/clone', () => ok(f.anItemDetail({ id: 'item-2', name: 'Royal Sherwani (copy)' }))),
    )
    const onClose = vi.fn()

    renderWithProviders(<ItemDetailDrawer itemId="item-1" onClose={onClose} />)
    await flush()

    await userEvent.click(screen.getByRole('button', { name: /clone/i }))
    await flush()

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('navigates to the edit page when Edit Item is clicked', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'item-1', photos: [] }))))
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })

    render(
      <QueryClientProvider client={queryClient}>
        <ConfigProvider>
          <MemoryRouter initialEntries={['/inventory']}>
            <Routes>
              <Route path="/inventory" element={<ItemDetailDrawer itemId="item-1" onClose={noop} />} />
              <Route path="/inventory/:id/edit" element={<div>Edit Item Page</div>} />
            </Routes>
          </MemoryRouter>
        </ConfigProvider>
      </QueryClientProvider>,
    )
    await flush()

    await userEvent.click(screen.getByRole('button', { name: 'Edit Item' }))

    expect(screen.getByText('Edit Item Page')).toBeInTheDocument()
  })

  it('asks for confirmation and deletes the item when confirmed', async () => {
    server.use(
      http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'item-1', photos: [] }))),
      http.delete('*/api/items/:id', () => ok(null)),
    )
    const onClose = vi.fn()

    renderWithProviders(<ItemDetailDrawer itemId="item-1" onClose={onClose} />)
    await flush()

    await userEvent.click(screen.getByRole('button', { name: /delete/i }))
    await screen.findAllByText('Delete Item')
    expect(screen.getByText(/cannot be undone/)).toBeInTheDocument()

    const confirmButtons = document.querySelector('.ant-modal-confirm-btns') as HTMLElement
    await userEvent.click(within(confirmButtons).getByRole('button', { name: 'Delete' }))
    await flush()

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('cancels deletion without calling onClose when Cancel is clicked', async () => {
    server.use(http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'item-1', photos: [] }))))
    const onClose = vi.fn()

    renderWithProviders(<ItemDetailDrawer itemId="item-1" onClose={onClose} />)
    await flush()

    await userEvent.click(screen.getByRole('button', { name: /delete/i }))
    await screen.findAllByText('Delete Item')

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    await flush()

    expect(onClose).not.toHaveBeenCalled()
  })

  it('shows an error message when deletion fails', async () => {
    server.use(
      http.get('*/api/items/:id', () => ok(f.anItemDetail({ id: 'item-1', photos: [] }))),
      http.delete('*/api/items/:id', () => HttpResponse.json({ success: false, data: null, error: 'Item is referenced by an active receipt' }, { status: 409 })),
    )
    const onClose = vi.fn()

    renderWithProviders(<ItemDetailDrawer itemId="item-1" onClose={onClose} />)
    await flush()

    await userEvent.click(screen.getByRole('button', { name: /delete/i }))
    await screen.findAllByText('Delete Item')
    const confirmButtons = document.querySelector('.ant-modal-confirm-btns') as HTMLElement
    await userEvent.click(within(confirmButtons).getByRole('button', { name: 'Delete' }))
    await flush()

    expect(onClose).not.toHaveBeenCalled()
  })
})

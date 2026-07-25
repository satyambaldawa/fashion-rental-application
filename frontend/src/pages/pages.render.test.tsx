import { describe, it, expect, beforeEach } from 'vitest'
import { renderWithProviders, flush } from '../test/render'
import { useAuthStore } from '../store/authStore'
import LoginPage from './LoginPage'
import SettingsPage from './SettingsPage'
import UnauthorizedPage from './UnauthorizedPage'
import CustomersPage from './customers/CustomersPage'
import RegisterCustomerPage from './customers/RegisterCustomerPage'
import ReceiptsPage from './receipts/ReceiptsPage'
import ReceiptDetailPage from './receipts/ReceiptDetailPage'
import ProcessReturnPage from './receipts/ProcessReturnPage'
import InvoiceDetailPage from './invoices/InvoiceDetailPage'
import InventoryPage from './inventory/InventoryPage'
import AddItemPage from './inventory/AddItemPage'
import ReportsPage from './reports/ReportsPage'
import CheckoutPage from './checkout/CheckoutPage'
import PublicReceiptPage from './public/PublicReceiptPage'
import PublicInvoicePage from './public/PublicInvoicePage'

// Owner token so isOwner-gated pages render their full content.
beforeEach(() => useAuthStore.setState({ token: 'test-token', role: 'OWNER' }))

describe('page smoke renders', () => {
  it('LoginPage renders a form', async () => {
    const { container } = renderWithProviders(<LoginPage />)
    await flush()
    expect(container.firstChild).toBeTruthy()
  })

  it('UnauthorizedPage renders', async () => {
    const { container } = renderWithProviders(<UnauthorizedPage />)
    await flush()
    expect(container.firstChild).toBeTruthy()
  })

  it('SettingsPage renders late-fee configuration', async () => {
    const { container } = renderWithProviders(<SettingsPage />)
    await flush()
    expect(container.firstChild).toBeTruthy()
  })

  it('CustomersPage renders', async () => {
    const { container } = renderWithProviders(<CustomersPage />)
    await flush()
    expect(container.firstChild).toBeTruthy()
  })

  it('RegisterCustomerPage renders in create mode', async () => {
    const { container } = renderWithProviders(<RegisterCustomerPage />, { route: '/customers/new' })
    await flush()
    expect(container.firstChild).toBeTruthy()
  })

  it('ReceiptsPage lists receipts', async () => {
    const { container } = renderWithProviders(<ReceiptsPage />)
    await flush()
    expect(container.firstChild).toBeTruthy()
  })

  it('ReceiptDetailPage renders for an id', async () => {
    const { container } = renderWithProviders(<ReceiptDetailPage />, {
      route: '/receipts/rcpt-1', path: '/receipts/:id',
    })
    await flush()
    expect(container.firstChild).toBeTruthy()
  })

  it('ProcessReturnPage renders for an id', async () => {
    const { container } = renderWithProviders(<ProcessReturnPage />, {
      route: '/receipts/rcpt-1/return', path: '/receipts/:id/return',
    })
    await flush()
    expect(container.firstChild).toBeTruthy()
  })

  it('InvoiceDetailPage renders for an id', async () => {
    const { container } = renderWithProviders(<InvoiceDetailPage />, {
      route: '/invoices/inv-1', path: '/invoices/:id',
    })
    await flush()
    expect(container.firstChild).toBeTruthy()
  })

  it('InventoryPage renders', async () => {
    const { container } = renderWithProviders(<InventoryPage />)
    await flush()
    expect(container.firstChild).toBeTruthy()
  })

  it('AddItemPage renders in create mode', async () => {
    const { container } = renderWithProviders(<AddItemPage />, { route: '/inventory/new' })
    await flush()
    expect(container.firstChild).toBeTruthy()
  })

  it('AddItemPage renders in edit mode', async () => {
    const { container } = renderWithProviders(<AddItemPage />, {
      route: '/inventory/item-1/edit', path: '/inventory/:id/edit',
    })
    await flush()
    expect(container.firstChild).toBeTruthy()
  })

  it('ReportsPage renders', async () => {
    const { container } = renderWithProviders(<ReportsPage />)
    await flush()
    expect(container.firstChild).toBeTruthy()
  })

  it('CheckoutPage renders', async () => {
    const { container } = renderWithProviders(<CheckoutPage />)
    await flush()
    expect(container.firstChild).toBeTruthy()
  })

  it('PublicReceiptPage renders for a share token', async () => {
    const { container } = renderWithProviders(<PublicReceiptPage />, {
      route: '/public/receipts/share-r', path: '/public/receipts/:shareToken',
    })
    await flush()
    expect(container.firstChild).toBeTruthy()
  })

  it('PublicInvoicePage renders for a share token', async () => {
    const { container } = renderWithProviders(<PublicInvoicePage />, {
      route: '/public/invoices/share-i', path: '/public/invoices/:shareToken',
    })
    await flush()
    expect(container.firstChild).toBeTruthy()
  })
})

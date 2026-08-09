import { describe, it, expect } from 'vitest'
import { http, HttpResponse } from 'msw'
import { renderWithProviders, flush, screen, within } from '../test/render'
import { server } from '../test/server'
import * as f from '../test/factories'
import ReceiptDetailPage from './receipts/ReceiptDetailPage'
import ProcessReturnPage from './receipts/ProcessReturnPage'
import PublicReceiptPage from './public/PublicReceiptPage'
import InvoiceDetailPage from './invoices/InvoiceDetailPage'
import PublicInvoicePage from './public/PublicInvoicePage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })

const receiptWithThumbnail = () => f.aReceipt({
  lineItems: [{
    id: 'rli-1', itemId: 'item-1', itemName: 'Royal Sherwani',
    thumbnailUrl: 'https://r2.example/thumb.jpg', itemSize: 'M',
    itemCategory: 'COSTUME', itemDescription: null, quantity: 1, rateSnapshot: 300,
    depositSnapshot: 1000, lineRent: 300, lineDeposit: 1000, itemPurchaseRate: 1500,
  }],
})

const invoiceWithThumbnail = () => f.anInvoice({
  lineItems: [{
    id: 'ili-1', itemId: 'item-1', itemName: 'Royal Sherwani',
    thumbnailUrl: 'https://r2.example/thumb.jpg', itemSize: 'M', itemCategory: 'COSTUME',
    quantityReturned: 1, rateSnapshot: 300, depositSnapshot: 1000, isDamaged: false,
    damagePercentage: null, damageCost: 0, lateFee: 0,
  }],
})

describe('line item thumbnails', () => {
  describe('ReceiptDetailPage', () => {
    it('renders an item photo when the line item has a thumbnailUrl', async () => {
      server.use(http.get('*/api/receipts/:id', () => ok(receiptWithThumbnail())))

      renderWithProviders(<ReceiptDetailPage />, { route: '/receipts/rcpt-1', path: '/receipts/:id' })
      await flush()

      const img = await screen.findByRole('img', { name: 'Royal Sherwani' })
      expect(img).toHaveAttribute('src', 'https://r2.example/thumb.jpg')
    })

    it('renders a placeholder when the line item has no thumbnailUrl', async () => {
      server.use(http.get('*/api/receipts/:id', () => ok(f.aReceipt())))

      renderWithProviders(<ReceiptDetailPage />, { route: '/receipts/rcpt-1', path: '/receipts/:id' })
      await flush()

      const nameCell = await screen.findByText('Royal Sherwani')
      const row = nameCell.closest('tr')!
      expect(within(row).queryByRole('img')).not.toBeInTheDocument()
      expect(row.querySelector('svg')).toBeInTheDocument()
    })
  })

  describe('ProcessReturnPage', () => {
    it('renders an item photo when the line item has a thumbnailUrl', async () => {
      server.use(http.get('*/api/receipts/:id', () => ok(receiptWithThumbnail())))

      renderWithProviders(<ProcessReturnPage />, { route: '/receipts/rcpt-1/return', path: '/receipts/:id/return' })
      await flush()

      const img = await screen.findByRole('img', { name: 'Royal Sherwani' })
      expect(img).toHaveAttribute('src', 'https://r2.example/thumb.jpg')
    })

    it('renders a placeholder when the line item has no thumbnailUrl', async () => {
      server.use(http.get('*/api/receipts/:id', () => ok(f.aReceipt())))

      renderWithProviders(<ProcessReturnPage />, { route: '/receipts/rcpt-1/return', path: '/receipts/:id/return' })
      await flush()

      const nameEl = await screen.findByText('Royal Sherwani')
      const card = nameEl.closest<HTMLElement>('.ant-card')!
      expect(within(card).queryByRole('img')).not.toBeInTheDocument()
      expect(card.querySelector('svg')).toBeInTheDocument()
    })
  })

  describe('PublicReceiptPage', () => {
    it('renders an item photo when the line item has a thumbnailUrl', async () => {
      server.use(http.get('*/api/public/receipts/:token', () => ok(receiptWithThumbnail())))

      renderWithProviders(<PublicReceiptPage />, {
        route: '/public/receipts/share-r', path: '/public/receipts/:shareToken',
      })
      await flush()

      const img = await screen.findByRole('img', { name: 'Royal Sherwani' })
      expect(img).toHaveAttribute('src', 'https://r2.example/thumb.jpg')
    })

    it('renders a placeholder when the line item has no thumbnailUrl', async () => {
      server.use(http.get('*/api/public/receipts/:token', () => ok(f.aReceipt())))

      renderWithProviders(<PublicReceiptPage />, {
        route: '/public/receipts/share-r', path: '/public/receipts/:shareToken',
      })
      await flush()

      const nameCell = await screen.findByText('Royal Sherwani')
      const row = nameCell.closest('tr')!
      expect(within(row).queryByRole('img')).not.toBeInTheDocument()
      expect(row.querySelector('svg')).toBeInTheDocument()
    })
  })

  describe('InvoiceDetailPage', () => {
    it('renders an item photo when the line item has a thumbnailUrl', async () => {
      server.use(http.get('*/api/invoices/:id', () => ok(invoiceWithThumbnail())))

      renderWithProviders(<InvoiceDetailPage />, { route: '/invoices/inv-1', path: '/invoices/:id' })
      await flush()

      const img = await screen.findByRole('img', { name: 'Royal Sherwani' })
      expect(img).toHaveAttribute('src', 'https://r2.example/thumb.jpg')
    })

    it('renders a placeholder when the line item has no thumbnailUrl', async () => {
      server.use(http.get('*/api/invoices/:id', () => ok(f.anInvoice())))

      renderWithProviders(<InvoiceDetailPage />, { route: '/invoices/inv-1', path: '/invoices/:id' })
      await flush()

      const nameCell = await screen.findByText('Royal Sherwani')
      const row = nameCell.closest('tr')!
      expect(within(row).queryByRole('img')).not.toBeInTheDocument()
      expect(row.querySelector('svg')).toBeInTheDocument()
    })
  })

  describe('PublicInvoicePage', () => {
    it('renders an item photo when the line item has a thumbnailUrl', async () => {
      server.use(http.get('*/api/public/invoices/:token', () => ok(invoiceWithThumbnail())))

      renderWithProviders(<PublicInvoicePage />, {
        route: '/public/invoices/share-i', path: '/public/invoices/:shareToken',
      })
      await flush()

      const img = await screen.findByRole('img', { name: 'Royal Sherwani' })
      expect(img).toHaveAttribute('src', 'https://r2.example/thumb.jpg')
    })

    it('renders a placeholder when the line item has no thumbnailUrl', async () => {
      server.use(http.get('*/api/public/invoices/:token', () => ok(f.anInvoice())))

      renderWithProviders(<PublicInvoicePage />, {
        route: '/public/invoices/share-i', path: '/public/invoices/:shareToken',
      })
      await flush()

      const nameCell = await screen.findByText('Royal Sherwani')
      const row = nameCell.closest('tr')!
      expect(within(row).queryByRole('img')).not.toBeInTheDocument()
      expect(row.querySelector('svg')).toBeInTheDocument()
    })
  })
})

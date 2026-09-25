import { http, HttpResponse } from 'msw'
import * as f from './factories'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })
const page = (content: unknown[]) => ({
  content, totalElements: content.length, totalPages: 1, number: 0, size: 20,
})

/** Default happy-path handlers. Individual tests override with `server.use(...)`. */
export const handlers = [
  // auth
  http.post('*/api/auth/login', () => ok(f.aLoginResponse())),
  http.get('*/api/auth/users', () => ok([f.aUserRecord()])),
  http.post('*/api/auth/users', () => ok(f.aUserRecord())),
  http.put('*/api/auth/users/:id', () => ok(f.aUserRecord())),
  http.delete('*/api/auth/users/:id', () => ok(null)),

  // items
  http.get('*/api/items/:id/availability', () =>
    ok({ itemId: 'item-1', availableQuantity: 3, isAvailable: true })),
  http.get('*/api/items/:id', () => ok(f.anItemDetail())),
  http.get('*/api/items', () => ok(page([f.anItemSummary()]))),
  http.post('*/api/items/:id/clone', () => ok(f.anItemDetail())),
  http.post('*/api/items/:id/photos', () =>
    ok({ id: 'photo-1', url: 'u', thumbnailUrl: 't', sortOrder: 0 })),
  http.post('*/api/items', () => ok(f.anItemDetail())),
  http.put('*/api/items/:id', () => ok(f.anItemDetail())),
  http.patch('*/api/items/:id/photos/order', () => ok(null)),
  http.delete('*/api/items/:itemId/photos/:photoId', () => ok(null)),
  http.delete('*/api/items/:id', () => ok(null)),

  // customers
  http.get('*/api/customers/:id/history', () => ok(f.aCustomerDetail())),
  http.get('*/api/customers/:id', () => ok(f.aCustomer())),
  http.get('*/api/customers', () => ok([f.aCustomerSummary()])),
  http.post('*/api/customers', () => ok(f.aCustomer())),
  http.put('*/api/customers/:id', () => ok(f.aCustomer())),

  // receipts / checkout
  http.post('*/api/checkout/preview', () => ok(f.aCheckoutPreview())),
  http.get('*/api/receipts/:id', () => ok(f.aReceipt())),
  http.get('*/api/receipts', () => ok([f.aReceiptSummary()])),
  http.post('*/api/receipts/:receiptId/return/preview', () => ok(f.aReturnPreview())),
  http.post('*/api/receipts/:receiptId/return', () => ok(f.anInvoice())),
  http.post('*/api/receipts', () => ok(f.aReceipt())),

  // invoices
  http.get('*/api/invoices/:id', () => ok(f.anInvoice())),

  // reports
  http.get('*/api/reports/daily-revenue', () => ok(f.aDailyRevenue())),
  http.get('*/api/reports/outstanding-deposits', () => ok(f.outstandingDeposits())),
  http.get('*/api/reports/overdue-rentals', () => ok(f.overdueRentals())),
  http.get('*/api/reports/monthly-revenue', () => ok(f.aMonthlyRevenue())),
  http.get('*/api/reports/discounts-given', () => ok(f.aDiscountsGiven())),

  // config
  http.get('*/api/config/late-fee-rules', () => ok([f.aLateFeeRule()])),
  http.put('*/api/config/late-fee-rules', () => ok([f.aLateFeeRule()])),

  // coupons
  http.get('*/api/config/coupons', () => ok([f.aCoupon()])),
  http.get('*/api/config/coupons/:id', () => ok(f.aCoupon())),
  http.post('*/api/config/coupons', () => ok(f.aCoupon())),
  http.put('*/api/config/coupons/:id', () => ok(f.aCoupon())),
  http.patch('*/api/config/coupons/:id/status', () => ok(f.aCoupon())),

  // public share pages
  http.get('*/api/public/receipts/:token', () => ok(f.aReceipt())),
  http.get('*/api/public/invoices/:token', () => ok(f.anInvoice())),

  // public gallery
  http.get('*/api/public/gallery', ({ request }) => {
    const category = new URL(request.url).searchParams.get('category')
    const images = [
      f.aGalleryImage({ id: 'gallery-1', category: 'COSTUME', caption: 'Royal Sherwani' }),
      f.aGalleryImage({
        id: 'gallery-2', category: 'PAGDI', caption: null,
        thumbnailUrl: 'https://cdn.example.com/gallery-2-thumb.jpg',
      }),
    ]
    return ok(category ? images.filter(image => image.category === category) : images)
  }),

  // admin gallery (authenticated) — listing includes inactive images
  http.get('*/api/gallery', ({ request }) => {
    const category = new URL(request.url).searchParams.get('category')
    const images = [
      f.aGalleryImage({ id: 'admin-1', category: 'COSTUME', caption: 'Royal Sherwani' }),
      f.aGalleryImage({ id: 'admin-2', category: 'COSTUME', caption: null, isActive: false }),
    ]
    return ok(category ? images.filter(image => image.category === category) : images)
  }),
  http.post('*/api/gallery', () =>
    HttpResponse.json(
      { success: true, data: [f.aGalleryImage({ id: 'uploaded-1' })], error: null },
      { status: 201 },
    ),
  ),
  http.patch('*/api/gallery/:id', async ({ request, params }) => {
    const body = (await request.json()) as { caption?: string; isActive?: boolean }
    return ok(f.aGalleryImage({ id: params.id as string, ...body }))
  }),
  http.delete('*/api/gallery/:id', () => ok(null)),

  // public reviews
  http.get('*/api/public/reviews', () => ok({
    content: [
      f.aPublicReview(),
      f.aPublicReview({
        id: 'review-2', reviewerName: 'Anil K', rating: 4, itemDescription: 'Maroon sherwani',
        reviewText: 'Great fit and friendly staff.', images: [],
        createdAt: '2026-09-18T11:00:00+05:30',
      }),
    ],
    totalElements: 2, totalPages: 1, number: 0, size: 10,
  })),
  http.post('*/api/public/reviews', () =>
    HttpResponse.json(
      { success: true, data: { id: 'review-3', status: 'PENDING' }, error: null },
      { status: 201 },
    ),
  ),
]

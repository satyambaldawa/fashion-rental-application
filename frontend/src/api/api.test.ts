import { describe, it, expect, afterEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../test/server'
import { itemsApi } from './items'
import { customersApi } from './customers'
import { receiptsApi } from './receipts'
import { invoicesApi } from './invoices'
import { reportsApi } from './reports'
import { authApi, login } from './auth'
import { publicApi } from './public'
import { getLateFeeRules, updateLateFeeRules } from './config'
import { useAuthStore } from '../store/authStore'

afterEach(() => useAuthStore.setState({ token: null, role: null }))

describe('itemsApi', () => {
  it('list unwraps the paged envelope', async () => {
    const page = await itemsApi.list({ page: 0, size: 20 })
    expect(page.content[0].name).toBe('Royal Sherwani')
    expect(page.totalElements).toBe(1)
  })

  it('get / create / update / clone return item detail', async () => {
    expect((await itemsApi.get('item-1')).id).toBe('item-1')
    expect((await itemsApi.create({} as never)).id).toBe('item-1')
    expect((await itemsApi.update('item-1', {} as never)).id).toBe('item-1')
    expect((await itemsApi.clone('item-1')).id).toBe('item-1')
  })

  it('checkAvailability returns available quantity', async () => {
    expect((await itemsApi.checkAvailability('item-1', 's', 'e')).availableQuantity).toBe(3)
  })

  it('photo delete/reorder and item delete resolve', async () => {
    await expect(itemsApi.deletePhoto('item-1', 'photo-1')).resolves.toBeTruthy()
    await expect(itemsApi.reorderPhotos('item-1', [{ id: 'photo-1', sortOrder: 0 }])).resolves.toBeTruthy()
    await expect(itemsApi.delete('item-1')).resolves.toBeTruthy()
  })
})

describe('customersApi', () => {
  it('search / get / create', async () => {
    expect((await customersApi.search({ phone: '98' }))[0].name).toBe('Meera')
    expect((await customersApi.get('cust-1')).id).toBe('cust-1')
    expect((await customersApi.create({} as never)).id).toBe('cust-1')
  })
})

describe('receiptsApi', () => {
  it('preview / create / list / get', async () => {
    expect((await receiptsApi.preview({} as never)).grandTotal).toBe(1300)
    expect((await receiptsApi.create({} as never)).receiptNumber).toBe('R-2026-0001')
    expect((await receiptsApi.list())[0].id).toBe('rcpt-1')
    expect((await receiptsApi.get('rcpt-1')).id).toBe('rcpt-1')
  })
})

describe('invoicesApi', () => {
  it('previewReturn / processReturn / get', async () => {
    expect((await invoicesApi.previewReturn('rcpt-1', {} as never)).transactionType).toBe('REFUND')
    expect((await invoicesApi.processReturn('rcpt-1', {} as never)).invoiceNumber).toBe('INV-2026-0001')
    expect((await invoicesApi.get('inv-1')).id).toBe('inv-1')
  })
})

describe('reportsApi', () => {
  it('covers all four reports and the optional-date branch', async () => {
    expect((await reportsApi.getDailyRevenue('2026-04-19')).netFlow).toBe(1300)
    expect((await reportsApi.getDailyRevenue()).netFlow).toBe(1300)
    expect((await reportsApi.getOutstandingDeposits()).totalOutstanding).toBe(1000)
    expect((await reportsApi.getOverdueRentals()).overdueCount).toBe(0)
    expect((await reportsApi.getMonthlyRevenue(2026, 4)).totalNetFlow).toBe(1300)
  })
})

describe('authApi', () => {
  it('login and user CRUD', async () => {
    expect((await authApi.login({ username: 'o', password: 'p' })).token).toBe('test-jwt-token')
    expect((await login({ username: 'o', password: 'p' })).role).toBe('OWNER')
    expect((await authApi.listUsers())[0].username).toBe('owner')
    expect((await authApi.createUser({} as never)).id).toBe('user-1')
    expect((await authApi.updateUser('user-1', {} as never)).id).toBe('user-1')
    await expect(authApi.deleteUser('user-1')).resolves.toBeTruthy()
  })
})

describe('publicApi', () => {
  it('getReceipt / getInvoice use the unauthenticated client', async () => {
    expect((await publicApi.getReceipt('share-r')).id).toBe('rcpt-1')
    expect((await publicApi.getInvoice('share-i')).id).toBe('inv-1')
  })
})

describe('config api', () => {
  it('gets and updates late-fee rules', async () => {
    expect((await getLateFeeRules())[0].id).toBe('rule-1')
    expect((await updateLateFeeRules({ rules: [] })).length).toBe(1)
  })
})

describe('client interceptors', () => {
  it('attaches the bearer token from the auth store', async () => {
    useAuthStore.setState({ token: 'my-token', role: 'OWNER' })
    let authHeader: string | null = null
    server.use(http.get('*/api/items/:id', ({ request }) => {
      authHeader = request.headers.get('authorization')
      return HttpResponse.json({ success: true, data: { id: 'item-1' }, error: null })
    }))
    await itemsApi.get('item-1')
    expect(authHeader).toBe('Bearer my-token')
  })

  it('clears the token when the server responds 401', async () => {
    useAuthStore.setState({ token: 'expired', role: 'OWNER' })
    server.use(http.get('*/api/items/:id', () => new HttpResponse(null, { status: 401 })))
    await expect(itemsApi.get('item-1')).rejects.toBeDefined()
    expect(useAuthStore.getState().token).toBeNull()
  })
})

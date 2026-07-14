import axios from 'axios'
import type { ApiResponse } from '../types/api'
import type { Receipt } from '../types/receipt'
import type { Invoice } from '../types/invoice'

// Unauthenticated client — no JWT, no 401 redirect
const publicClient = axios.create({
  baseURL: `${import.meta.env.VITE_API_URL ?? ''}/api/public`,
  headers: { 'Content-Type': 'application/json' },
})

export const publicApi = {
  getReceipt: (shareToken: string): Promise<Receipt> =>
    publicClient.get<ApiResponse<Receipt>>(`/receipts/${shareToken}`).then(r => r.data.data!),

  getInvoice: (shareToken: string): Promise<Invoice> =>
    publicClient.get<ApiResponse<Invoice>>(`/invoices/${shareToken}`).then(r => r.data.data!),
}

import client from './client'
import type { ApiResponse } from '../types/api'
import type {
  CheckoutPreview,
  CheckoutPreviewRequest,
  CheckoutRequest,
  Receipt,
  ReceiptSummary,
} from '../types/receipt'

export const receiptsApi = {
  preview: (data: CheckoutPreviewRequest): Promise<CheckoutPreview> =>
    client.post<ApiResponse<CheckoutPreview>>('/checkout/preview', data).then(r => r.data.data!),

  create: (data: CheckoutRequest): Promise<Receipt> =>
    client.post<ApiResponse<Receipt>>('/receipts', data).then(r => r.data.data!),

  list: (params?: { status?: string; overdue?: boolean }): Promise<ReceiptSummary[]> =>
    client.get<ApiResponse<ReceiptSummary[]>>('/receipts', { params }).then(r => r.data.data!),

  get: (id: string): Promise<Receipt> =>
    client.get<ApiResponse<Receipt>>(`/receipts/${id}`).then(r => r.data.data!),
}

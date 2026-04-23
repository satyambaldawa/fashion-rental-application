import client from './client'
import type { ApiResponse } from '../types/api'
import type { Invoice, ProcessReturnRequest, ReturnPreview } from '../types/invoice'

export const invoicesApi = {
  previewReturn: (receiptId: string, data: ProcessReturnRequest) =>
    client.post<ApiResponse<ReturnPreview>>(`/receipts/${receiptId}/return/preview`, data)
      .then(r => r.data.data!),

  processReturn: (receiptId: string, data: ProcessReturnRequest) =>
    client.post<ApiResponse<Invoice>>(`/receipts/${receiptId}/return`, data)
      .then(r => r.data.data!),

  get: (id: string) =>
    client.get<ApiResponse<Invoice>>(`/invoices/${id}`).then(r => r.data.data!),
}

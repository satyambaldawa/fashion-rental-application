import client from './client'
import type { ApiResponse } from '../types/api'
import type { PaymentSettings, UpdatePaymentSettingsRequest } from '../types/paymentSettings'

export const paymentSettingsApi = {
  // OWNER only
  get: (): Promise<PaymentSettings> =>
    client.get<ApiResponse<PaymentSettings>>('/config/payment-settings').then(r => r.data.data!),

  // OWNER only
  update: (data: UpdatePaymentSettingsRequest): Promise<PaymentSettings> =>
    client.put<ApiResponse<PaymentSettings>>('/config/payment-settings', data).then(r => r.data.data!),

  // Any staff role — what checkout may offer
  checkoutOptions: (): Promise<PaymentSettings> =>
    client.get<ApiResponse<PaymentSettings>>('/checkout/payment-options').then(r => r.data.data!),
}

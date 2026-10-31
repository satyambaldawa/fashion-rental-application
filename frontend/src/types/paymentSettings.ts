export interface PaymentSettings {
  upiEnabled: boolean
  upiVpa: string | null
  upiPayeeName: string | null
}

// A blank upiVpa disables UPI at checkout.
export interface UpdatePaymentSettingsRequest {
  upiVpa: string
  upiPayeeName: string
}

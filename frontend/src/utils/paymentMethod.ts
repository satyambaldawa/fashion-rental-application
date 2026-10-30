import type { ReceiptPaymentMethod } from '../types/receipt'

export const PAYMENT_METHOD_LABELS: Record<ReceiptPaymentMethod, string> = {
  CASH: 'Cash',
  UPI: 'UPI',
}

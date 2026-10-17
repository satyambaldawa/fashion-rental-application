import type { CancellationReason, Receipt } from '../types/receipt'

// Only a booking whose dress has not left the shop can be cancelled. The server is the
// authority; this only decides whether to show the button.
export function isReceiptCancellable(receipt: Pick<Receipt, 'status'>): boolean {
  return receipt.status === 'GENERATED'
}

export const CANCELLATION_REASON_LABELS: Record<CancellationReason, string> = {
  WRONG_ORDER: 'Wrong order',
  CUSTOMER_DOES_NOT_WANT: "Customer doesn't want it",
  CHANGE_ORDER_DATES: 'Change order dates',
  OTHER: 'Other',
}

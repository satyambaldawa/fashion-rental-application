import dayjs, { type Dayjs } from 'dayjs'
import type { CancellationReason, Receipt } from '../types/receipt'

export const CANCELLATION_CUTOFF_HOURS = 12

const CANCELLATION_CUTOFF_MS = CANCELLATION_CUTOFF_HOURS * 60 * 60 * 1000

// The server is the authority on eligibility — if the tablet's clock is off, the server's
// 409 is shown to the user. This only decides whether to show the button.
export function isReceiptCancellable(
  receipt: Pick<Receipt, 'status' | 'endDatetime'>,
  now: Dayjs = dayjs()
): boolean {
  return receipt.status === 'GIVEN' && dayjs(receipt.endDatetime).diff(now, 'millisecond') > CANCELLATION_CUTOFF_MS
}

export const CANCELLATION_REASON_LABELS: Record<CancellationReason, string> = {
  WRONG_ORDER: 'Wrong order',
  CUSTOMER_DOES_NOT_WANT: "Customer doesn't want it",
  CHANGE_ORDER_DATES: 'Change order dates',
  OTHER: 'Other',
}

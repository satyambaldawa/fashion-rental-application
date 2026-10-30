import dayjs, { type Dayjs } from 'dayjs'
import type { Receipt } from '../types/receipt'

// Only a Given receipt whose booked start has not yet passed can have its hand-over undone.
// The server is the authority (403 for non-owners, 409 once the start passes); this only
// decides whether to show the button.
export function isGivenUndoable(receipt: Pick<Receipt, 'status' | 'startDatetime'>, now: Dayjs = dayjs()): boolean {
  return receipt.status === 'GIVEN' && dayjs(receipt.startDatetime).isAfter(now)
}

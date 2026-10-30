import dayjs, { type Dayjs } from 'dayjs'
import type { Receipt } from '../types/receipt'

export function isGivenUndoable(receipt: Pick<Receipt, 'status' | 'startDatetime'>, now: Dayjs = dayjs()): boolean {
  return receipt.status === 'GIVEN' && dayjs(receipt.startDatetime).isAfter(now)
}

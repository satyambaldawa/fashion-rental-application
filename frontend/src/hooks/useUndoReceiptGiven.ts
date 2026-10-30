import { receiptsApi } from '../api/receipts'
import { useReceiptHandoverMutation } from './useReceiptHandoverMutation'

export function useUndoReceiptGiven(receiptId: string) {
  return useReceiptHandoverMutation(receiptId, receiptsApi.undoGive, {
    success: 'Hand-over undone',
    failure: 'Failed to undo hand-over',
  })
}

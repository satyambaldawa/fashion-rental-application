import { receiptsApi } from '../api/receipts'
import { useReceiptHandoverMutation } from './useReceiptHandoverMutation'

export function useMarkReceiptGiven(receiptId: string) {
  return useReceiptHandoverMutation(receiptId, receiptsApi.give, {
    success: 'Marked as given',
    failure: 'Failed to mark receipt as given',
  })
}

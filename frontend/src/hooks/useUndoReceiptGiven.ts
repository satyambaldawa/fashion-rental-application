import { useMutation, useQueryClient } from '@tanstack/react-query'
import { message } from 'antd'
import { receiptsApi } from '../api/receipts'

export function useUndoReceiptGiven(receiptId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => receiptsApi.undoGive(receiptId),
    onSuccess: (updated) => {
      queryClient.setQueryData(['receipt', receiptId], updated)
      queryClient.invalidateQueries({ queryKey: ['receipts'] })
      queryClient.invalidateQueries({ queryKey: ['customers'] })
      queryClient.invalidateQueries({ queryKey: ['reports'] })
      message.success('Hand-over undone')
    },
    onError: (err: unknown) => {
      const apiError = err as { response?: { data?: { error?: string } } }
      message.error(apiError.response?.data?.error ?? 'Failed to undo hand-over')
    },
  })
}

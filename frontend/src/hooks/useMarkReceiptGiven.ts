import { useMutation, useQueryClient } from '@tanstack/react-query'
import { message } from 'antd'
import { receiptsApi } from '../api/receipts'

export function useMarkReceiptGiven(receiptId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => receiptsApi.give(receiptId),
    onSuccess: (updated) => {
      queryClient.setQueryData(['receipt', receiptId], updated)
      queryClient.invalidateQueries({ queryKey: ['receipts'] })
      queryClient.invalidateQueries({ queryKey: ['customers'] })
      queryClient.invalidateQueries({ queryKey: ['reports'] })
      message.success('Marked as given')
    },
    onError: (err: unknown) => {
      const apiError = err as { response?: { data?: { error?: string } } }
      message.error(apiError.response?.data?.error ?? 'Failed to mark receipt as given')
    },
  })
}

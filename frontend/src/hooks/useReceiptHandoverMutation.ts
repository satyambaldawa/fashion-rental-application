import { useMutation, useQueryClient } from '@tanstack/react-query'
import { message } from 'antd'
import type { Receipt } from '../types/receipt'

interface HandoverMessages {
  success: string
  failure: string
}

export function useReceiptHandoverMutation(
  receiptId: string,
  sendHandoverRequest: (receiptId: string) => Promise<Receipt>,
  messages: HandoverMessages,
) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => sendHandoverRequest(receiptId),
    onSuccess: (updated) => {
      queryClient.setQueryData(['receipt', receiptId], updated)
      queryClient.invalidateQueries({ queryKey: ['receipts'] })
      queryClient.invalidateQueries({ queryKey: ['customers'] })
      queryClient.invalidateQueries({ queryKey: ['reports'] })
      message.success(messages.success)
    },
    onError: (err: unknown) => {
      const apiError = err as { response?: { data?: { error?: string } } }
      message.error(apiError.response?.data?.error ?? messages.failure)
    },
  })
}

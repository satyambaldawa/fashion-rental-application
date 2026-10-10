import { useState, type CSSProperties } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Button, Form, Input, message, Modal, Radio, Tooltip } from 'antd'
import { DeleteOutlined } from '@ant-design/icons'
import { receiptsApi } from '../../api/receipts'
import type { CancelReceiptRequest, CancellationReason, Receipt } from '../../types/receipt'
import { CANCELLATION_REASON_LABELS } from '../../utils/receiptCancellation'
import { formatCurrency } from '../../utils/currency'

interface ReasonFormValues {
  reason: CancellationReason
  reasonDetail?: string
}

interface CancelReceiptFlowProps {
  // Only what the flow uses, so both the detail page (Receipt) and list cards (ReceiptSummary) can pass theirs.
  receipt: Pick<Receipt, 'id' | 'receiptNumber' | 'grandTotal'>
  // 'icon' is the compact bin button used on list cards; 'button' is the labelled one on the detail page.
  trigger?: 'button' | 'icon'
  triggerStyle?: CSSProperties
}

type Step = 'idle' | 'confirm' | 'reason'

export default function CancelReceiptFlow({ receipt, trigger = 'button', triggerStyle }: CancelReceiptFlowProps) {
  const [step, setStep] = useState<Step>('idle')
  const [form] = Form.useForm<ReasonFormValues>()
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: (data: CancelReceiptRequest) => receiptsApi.cancel(receipt.id, data),
    onSuccess: (updated) => {
      queryClient.setQueryData(['receipt', receipt.id], updated)
      queryClient.invalidateQueries({ queryKey: ['receipts'] })
      queryClient.invalidateQueries({ queryKey: ['customers'] })
      queryClient.invalidateQueries({ queryKey: ['reports'] })
      queryClient.invalidateQueries({ queryKey: ['items-for-cart'] })
      queryClient.invalidateQueries({ queryKey: ['items-for-preview'] })
      queryClient.invalidateQueries({ queryKey: ['items'] })
      message.success('Receipt cancelled')
      setStep('idle')
    },
    onError: (err: unknown) => {
      const apiError = err as { response?: { data?: { error?: string } } }
      message.error(apiError.response?.data?.error ?? 'Failed to cancel receipt')
    },
  })

  const reasonWatch = Form.useWatch('reason', form)

  function closeReasonModal() {
    setStep('idle')
    form.resetFields()
  }

  async function handleReasonSubmit() {
    let values: ReasonFormValues
    try {
      values = await form.validateFields()
    } catch {
      // Field errors are already shown by the form itself; nothing more to do here.
      return
    }
    const reasonDetail = values.reason === 'OTHER' ? (values.reasonDetail ?? '').trim() : null
    mutation.mutate({ reason: values.reason, reasonDetail })
  }

  return (
    <>
      {trigger === 'icon' ? (
        <Tooltip title="Cancel receipt">
          <Button
            danger
            icon={<DeleteOutlined />}
            aria-label="Cancel receipt"
            style={triggerStyle}
            onClick={() => setStep('confirm')}
          />
        </Tooltip>
      ) : (
        <Button danger size="large" onClick={() => setStep('confirm')}>
          Cancel receipt
        </Button>
      )}

      <Modal
        title="Cancel this receipt?"
        open={step === 'confirm'}
        okText="Yes, money returned. Continue"
        okButtonProps={{ danger: true }}
        cancelText="No"
        onOk={() => setStep('reason')}
        onCancel={() => setStep('idle')}
        destroyOnClose
      >
        <p>
          Do you really want to cancel receipt {receipt.receiptNumber}? Only continue if{' '}
          {formatCurrency(receipt.grandTotal)} (rent net of discount plus deposit) has been returned to the
          customer.
        </p>
      </Modal>

      <Modal
        title="Why is this receipt being cancelled?"
        open={step === 'reason'}
        okText="Confirm cancellation"
        cancelText="Close"
        okButtonProps={{ danger: true, loading: mutation.isPending }}
        onOk={handleReasonSubmit}
        onCancel={closeReasonModal}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="reason"
            rules={[{ required: true, message: 'Please choose a reason' }]}
          >
            <Radio.Group>
              {(Object.keys(CANCELLATION_REASON_LABELS) as CancellationReason[]).map((reason) => (
                <Radio key={reason} value={reason} style={{ display: 'block', marginBottom: 8 }}>
                  {CANCELLATION_REASON_LABELS[reason]}
                </Radio>
              ))}
            </Radio.Group>
          </Form.Item>
          {reasonWatch === 'OTHER' && (
            <Form.Item
              name="reasonDetail"
              rules={[{ required: true, whitespace: true, message: 'Please describe the reason' }]}
            >
              <Input.TextArea maxLength={500} showCount placeholder="Describe the reason" rows={3} />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </>
  )
}

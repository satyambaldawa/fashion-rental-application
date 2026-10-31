import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Alert, Button, Descriptions, Modal, Space, Spin, Typography } from 'antd'
import { QRCodeSVG } from 'qrcode.react'
import { receiptsApi } from '../../api/receipts'
import { formatCurrency } from '../../utils/currency'
import { buildUpiPaymentUri } from '../../utils/upi'
import type { CheckoutPreviewRequest, CheckoutRequest, Receipt } from '../../types/receipt'

const { Text, Title } = Typography

const QR_SIZE_PX = 240
const UPI_NOTE = 'Rental'

// Mount only while the QR should be shown: each mount is one payment attempt.
interface UpiPaymentModalProps {
  vpa: string
  payeeName: string
  previewRequest: CheckoutPreviewRequest
  // Everything except the UPI-specific fields, which this modal owns.
  checkoutRequest: Omit<CheckoutRequest, 'paymentMethod' | 'expectedGrandTotal' | 'idempotencyKey'>
  onCancel: () => void
  onReceiptCreated: (receipt: Receipt) => void
}

type ApiError = { response?: { data?: { error?: string } } }

export default function UpiPaymentModal({
  vpa, payeeName, previewRequest, checkoutRequest, onCancel, onReceiptCreated,
}: UpiPaymentModalProps) {
  // One key per payment attempt: retrying "received" after a lost response replays the same
  // key, so the server returns the receipt it already created instead of a second one.
  const [idempotencyKey] = useState(() => crypto.randomUUID())

  // The QR amount is money actually collected, so it must come from the server's own pricing
  // at this moment — never from the cart's locally cached rates (see useCart's display-only
  // note). This also re-checks availability before the customer pays.
  const previewQuery = useQuery({
    queryKey: ['upi-payment-preview', idempotencyKey],
    queryFn: () => receiptsApi.preview(previewRequest),
    staleTime: Infinity,
    gcTime: 0,
  })

  const createMutation = useMutation({
    mutationFn: (amount: number) => receiptsApi.create({
      ...checkoutRequest,
      paymentMethod: 'UPI',
      expectedGrandTotal: amount,
      idempotencyKey,
    }),
    onSuccess: onReceiptCreated,
  })

  const preview = previewQuery.data
  const isConfirming = createMutation.isPending

  function renderBody() {
    if (previewQuery.isLoading) {
      return <div style={{ textAlign: 'center', padding: 32 }}><Spin /></div>
    }
    if (previewQuery.isError || !preview) {
      return <Alert type="error" message="Could not load the amount. Close and try again — do not ask the customer to pay yet." />
    }
    if (!preview.allAvailable) {
      return (
        <Alert
          type="error"
          message="Some items are no longer available. Do not take payment."
          description={preview.unavailableItems.join(', ')}
        />
      )
    }

    const upiUri = buildUpiPaymentUri({ vpa, payeeName, amountRupees: preview.grandTotal, note: UPI_NOTE })

    return (
      <Space direction="vertical" size="middle" style={{ width: '100%', alignItems: 'center' }}>
        <Title level={2} style={{ margin: 0 }}>{formatCurrency(preview.grandTotal)}</Title>
        <div style={{ background: '#fff', padding: 12 }}>
          <QRCodeSVG value={upiUri} size={QR_SIZE_PX} />
        </div>
        <Text>Pay to <strong>{payeeName}</strong> ({vpa})</Text>

        <Descriptions column={1} size="small" style={{ width: '100%' }}>
          <Descriptions.Item label="Rent">{formatCurrency(preview.totalRent)}</Descriptions.Item>
          {preview.discountAmount > 0 && (
            <Descriptions.Item label={`Discount (${preview.couponCode})`}>
              −{formatCurrency(preview.discountAmount)}
            </Descriptions.Item>
          )}
          <Descriptions.Item label="Deposit">{formatCurrency(preview.totalDeposit)}</Descriptions.Item>
        </Descriptions>

        <Alert
          type="warning"
          showIcon
          style={{ width: '100%' }}
          message={`Confirm only after ${formatCurrency(preview.grandTotal)} shows on YOUR soundbox or business app. Do not trust the customer's phone screen.`}
        />

        {createMutation.isError && (
          <Alert
            type="error"
            style={{ width: '100%' }}
            message={describeCreateFailure(createMutation.error as ApiError, preview.grandTotal)}
          />
        )}
      </Space>
    )
  }

  const canConfirm = !!preview && preview.allAvailable

  return (
    <Modal
      open
      title="UPI payment"
      onCancel={onCancel}
      maskClosable={false}
      closable={!isConfirming}
      footer={[
        <Button key="cancel" onClick={onCancel} disabled={isConfirming}>
          Cancel
        </Button>,
        <Button
          key="received"
          type="primary"
          disabled={!canConfirm}
          loading={isConfirming}
          onClick={() => preview && createMutation.mutate(preview.grandTotal)}
        >
          {canConfirm ? `${formatCurrency(preview.grandTotal)} received` : 'Payment received'}
        </Button>,
      ]}
    >
      {renderBody()}
    </Modal>
  )
}

// The customer has paid by the time this can fail, so every message says so and what to do.
function describeCreateFailure(error: ApiError, paidAmount: number): string {
  const paid = formatCurrency(paidAmount)
  if (!error.response) {
    return `No response from the server — the receipt may or may not have been created. Customer has paid ${paid}. Tapping "received" again is safe: it will not create a second receipt.`
  }
  const reason = error.response.data?.error ?? 'The receipt could not be created.'
  return `${reason} Receipt NOT created, but the customer has paid ${paid}. Do not let them leave: fix the order and create the receipt, or refund ${paid} by UPI.`
}

import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery, useMutation } from '@tanstack/react-query'
import {
  Alert,
  Button,
  Card,
  Checkbox,
  DatePicker,
  Descriptions,
  Divider,
  Form,
  InputNumber,
  Radio,
  Space,
  Tag,
  Typography,
} from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { receiptsApi } from '../../api/receipts'
import { invoicesApi } from '../../api/invoices'
import type { ReceiptLineItem } from '../../types/receipt'
import type {
  ProcessReturnRequest,
  ReturnLineItemRequest,
  ReturnPreview,
  PaymentMethod,
} from '../../types/invoice'
import { formatCurrency } from '../../utils/currency'
import { toApiDatetime } from '../../utils/datetime'

interface LineItemFormState {
  isDamaged: boolean
  damageMode: 'percentage' | 'adhoc'
  damagePercentage?: number
  adHocDamageAmount?: number
}

export default function ProcessReturnPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const [returnDatetime, setReturnDatetime] = useState<Dayjs>(dayjs())
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CASH')
  const [damageNotes, setDamageNotes] = useState('')
  const [notes, setNotes] = useState('')
  const [lineStates, setLineStates] = useState<Record<string, LineItemFormState>>({})
  const [preview, setPreview] = useState<ReturnPreview | null>(null)

  const { data: receipt, isLoading, isError } = useQuery({
    queryKey: ['receipt', id],
    queryFn: () => receiptsApi.get(id!),
    enabled: !!id,
  })

  // Initialise line states once receipt loads
  const [initialised, setInitialised] = useState(false)
  if (receipt && !initialised) {
    const initial: Record<string, LineItemFormState> = {}
    receipt.lineItems.forEach(li => {
      initial[li.id] = { isDamaged: false, damageMode: 'percentage' }
    })
    setLineStates(initial)
    setInitialised(true)
  }

  const previewMutation = useMutation({
    mutationFn: (req: ProcessReturnRequest) => invoicesApi.previewReturn(id!, req),
    onSuccess: setPreview,
  })

  const confirmMutation = useMutation({
    mutationFn: (req: ProcessReturnRequest) => invoicesApi.processReturn(id!, req),
    onSuccess: (invoice) => navigate(`/invoices/${invoice.id}`),
  })

  if (isLoading) return <Typography.Text>Loading receipt…</Typography.Text>
  if (isError || !receipt) return <Typography.Text type="danger">Receipt not found.</Typography.Text>
  if (receipt.status === 'RETURNED') {
    return (
      <div>
        <Typography.Title level={4}>Already Returned</Typography.Title>
        <Alert type="warning" message={`${receipt.receiptNumber} has already been returned.`} style={{ marginBottom: 16 }} />
        <Button onClick={() => navigate('/receipts')}>Back to Active Rentals</Button>
      </div>
    )
  }

  function buildRequest(): ProcessReturnRequest {
    const lineItems: ReturnLineItemRequest[] = receipt!.lineItems.map(li => {
      const state = lineStates[li.id] ?? { isDamaged: false, damageMode: 'percentage' }
      // When no purchase rate the percentage radio is hidden and damageMode stays 'percentage',
      // so we must fall back to adHoc regardless of damageMode in that case.
      const useAdHoc = state.damageMode === 'adhoc' || li.itemPurchaseRate == null
      return {
        receiptLineItemId: li.id,
        isDamaged: state.isDamaged,
        damagePercentage: state.isDamaged && !useAdHoc ? state.damagePercentage : undefined,
        adHocDamageAmount: state.isDamaged && useAdHoc ? state.adHocDamageAmount : undefined,
      }
    })
    return {
      returnDatetime: toApiDatetime(returnDatetime),
      lineItems,
      paymentMethod,
      damageNotes: damageNotes || undefined,
      notes: notes || undefined,
    }
  }

  function updateLine(lineId: string, patch: Partial<LineItemFormState>) {
    setLineStates(prev => ({ ...prev, [lineId]: { ...prev[lineId], ...patch } }))
    setPreview(null)
  }

  const isOverdue = returnDatetime.isAfter(dayjs(receipt.endDatetime))

  return (
    <div style={{ maxWidth: 780 }}>
      <Button
        type="link"
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate(`/receipts/${id}`)}
        style={{ padding: 0, marginBottom: 16 }}
      >
        Back to Receipt
      </Button>

      <Typography.Title level={4} style={{ marginBottom: 20 }}>
        Process Return — {receipt.receiptNumber}
      </Typography.Title>

      {/* Receipt summary */}
      <Descriptions bordered column={2} size="small" style={{ marginBottom: 24 }}>
        <Descriptions.Item label="Customer">{receipt.customerName}</Descriptions.Item>
        <Descriptions.Item label="Phone">{receipt.customerPhone}</Descriptions.Item>
        <Descriptions.Item label="Due By">
          <span style={{ color: isOverdue ? '#ff4d4f' : undefined, fontWeight: 500 }}>
            {dayjs(receipt.endDatetime).format('DD MMM YYYY, h:mm A')}
            {isOverdue && ' 🔴 Overdue'}
          </span>
        </Descriptions.Item>
        <Descriptions.Item label="Deposit Collected">
          {formatCurrency(receipt.totalDeposit)}
        </Descriptions.Item>
      </Descriptions>

      {/* Return datetime */}
      <Form layout="vertical">
        <Form.Item label="Return Date &amp; Time" required>
          <DatePicker
            showTime={{ format: 'HH:mm' }}
            format="DD MMM YYYY HH:mm"
            value={returnDatetime}
            onChange={(v) => { setReturnDatetime(v ?? dayjs()); setPreview(null) }}
            style={{ width: 240 }}
          />
        </Form.Item>
      </Form>

      {/* Line items */}
      <Typography.Title level={5} style={{ marginBottom: 12 }}>Items</Typography.Title>
      {receipt.lineItems.map((li: ReceiptLineItem) => {
        const state = lineStates[li.id] ?? { isDamaged: false, damageMode: 'percentage' }
        return (
          <Card
            key={li.id}
            size="small"
            style={{ marginBottom: 12 }}
            bodyStyle={{ padding: '12px 16px' }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <Typography.Text strong>{li.itemName}</Typography.Text>
                {li.itemSize && <Tag style={{ marginLeft: 8 }}>{li.itemSize}</Tag>}
                <Typography.Text type="secondary" style={{ marginLeft: 8, fontSize: 13 }}>
                  ×{li.quantity} · {formatCurrency(li.rateSnapshot)}/day
                </Typography.Text>
                <div>
                  <Typography.Text type="secondary" style={{ fontSize: 11, fontFamily: 'monospace' }}>
                    #{li.itemId.slice(0, 8)}
                  </Typography.Text>
                </div>
              </div>
              <Checkbox
                checked={state.isDamaged}
                onChange={e => updateLine(li.id, { isDamaged: e.target.checked })}
              >
                Damaged
              </Checkbox>
            </div>

            {state.isDamaged && (
              <div style={{ marginTop: 12, paddingTop: 12, borderTop: '1px solid #f0f0f0' }}>
                {li.itemPurchaseRate == null ? (
                  <div style={{ marginBottom: 8 }}>
                    <Typography.Text type="warning" style={{ fontSize: 13 }}>
                      No purchase cost available for this item — please enter a fixed damage amount.
                    </Typography.Text>
                  </div>
                ) : (
                  <Radio.Group
                    value={state.damageMode}
                    onChange={e => updateLine(li.id, { damageMode: e.target.value, damagePercentage: undefined, adHocDamageAmount: undefined })}
                    style={{ marginBottom: 8 }}
                  >
                    <Radio value="percentage">Damage % (of purchase cost ₹{li.itemPurchaseRate})</Radio>
                    <Radio value="adhoc">Fixed Amount (₹)</Radio>
                  </Radio.Group>
                )}
                {(li.itemPurchaseRate != null && state.damageMode === 'percentage') ? (
                  <InputNumber
                    min={1} max={100}
                    placeholder="e.g. 30"
                    addonAfter="%"
                    value={state.damagePercentage}
                    onChange={v => updateLine(li.id, { damagePercentage: v ?? undefined })}
                    style={{ width: 180 }}
                  />
                ) : (
                  <InputNumber
                    min={1}
                    placeholder="e.g. 500"
                    addonBefore="₹"
                    value={state.adHocDamageAmount}
                    onChange={v => updateLine(li.id, { adHocDamageAmount: v ?? undefined })}
                    style={{ width: 180 }}
                  />
                )}
              </div>
            )}
          </Card>
        )
      })}

      {/* Payment & notes */}
      <Form layout="vertical" style={{ marginTop: 8 }}>
        <Form.Item label="Payment Method">
          <Radio.Group value={paymentMethod} onChange={e => setPaymentMethod(e.target.value)}>
            <Radio value="CASH">Cash</Radio>
            <Radio value="UPI">UPI</Radio>
            <Radio value="OTHER">Other</Radio>
          </Radio.Group>
        </Form.Item>
        <Form.Item label="Damage Notes">
          <textarea
            rows={2}
            placeholder="Optional notes about damage"
            value={damageNotes}
            onChange={e => { setDamageNotes(e.target.value); setPreview(null) }}
            style={{ width: '100%', padding: 8, borderRadius: 6, border: '1px solid #d9d9d9', fontSize: 14, resize: 'vertical' }}
          />
        </Form.Item>
        <Form.Item label="Notes">
          <textarea
            rows={2}
            placeholder="Optional general notes"
            value={notes}
            onChange={e => { setNotes(e.target.value); setPreview(null) }}
            style={{ width: '100%', padding: 8, borderRadius: 6, border: '1px solid #d9d9d9', fontSize: 14, resize: 'vertical' }}
          />
        </Form.Item>
      </Form>

      <Divider />

      {/* Preview section */}
      {preview && (
        <Card
          style={{ marginBottom: 20, background: '#fafafa', borderColor: '#d9d9d9' }}
          title="Invoice Preview"
        >
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="Rent Charged">{formatCurrency(receipt.totalRent)}</Descriptions.Item>
            <Descriptions.Item label="Deposit Collected">{formatCurrency(receipt.totalDeposit)}</Descriptions.Item>
            {preview.totalLateFee > 0 && (
              <Descriptions.Item label="Late Fee">
                <span style={{ color: '#ff4d4f' }}>– {formatCurrency(preview.totalLateFee)}</span>
              </Descriptions.Item>
            )}
            {preview.totalDamageCost > 0 && (
              <Descriptions.Item label="Damage Cost">
                <span style={{ color: '#ff4d4f' }}>– {formatCurrency(preview.totalDamageCost)}</span>
              </Descriptions.Item>
            )}
            <Descriptions.Item label="Total Deductions">{formatCurrency(preview.totalDeductions)}</Descriptions.Item>
            <Descriptions.Item label="Deposit to Return">{formatCurrency(preview.depositToReturn)}</Descriptions.Item>
          </Descriptions>

          <div style={{
            marginTop: 16,
            padding: '14px 20px',
            background: preview.transactionType === 'REFUND' ? '#f6ffed' : '#fff2f0',
            border: `2px solid ${preview.transactionType === 'REFUND' ? '#52c41a' : '#ff4d4f'}`,
            borderRadius: 8,
            textAlign: 'center',
          }}>
            <Typography.Title
              level={3}
              style={{ margin: 0, color: preview.transactionType === 'REFUND' ? '#52c41a' : '#ff4d4f' }}
            >
              {preview.transactionType === 'REFUND' ? 'REFUND' : 'COLLECT'}: {formatCurrency(preview.finalAmount)}
            </Typography.Title>
          </div>
        </Card>
      )}

      {previewMutation.isError && (
        <Alert type="error" message="Failed to calculate preview. Please try again." style={{ marginBottom: 16 }} />
      )}
      {confirmMutation.isError && (
        <Alert type="error" message="Failed to process return. Please try again." style={{ marginBottom: 16 }} />
      )}

      <Space>
        <Button
          onClick={() => previewMutation.mutate(buildRequest())}
          loading={previewMutation.isPending}
        >
          Calculate Preview
        </Button>
        <Button
          type="primary"
          disabled={!preview}
          loading={confirmMutation.isPending}
          onClick={() => confirmMutation.mutate(buildRequest())}
        >
          Confirm &amp; Complete Return
        </Button>
      </Space>
    </div>
  )
}

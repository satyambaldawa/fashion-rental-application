import { useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  Button,
  Descriptions,
  Divider,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import { ArrowLeftOutlined, PrinterOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { receiptsApi } from '../../api/receipts'
import type { ReceiptLineItem } from '../../types/receipt'
import { formatCurrency } from '../../utils/currency'

const PRINT_STYLES = `
@media print {
  body * { visibility: hidden !important; }
  .receipt-print-area, .receipt-print-area * { visibility: visible !important; }
  .receipt-print-area {
    position: fixed !important;
    inset: 0 !important;
    padding: 24px !important;
    background: white !important;
  }
  .no-print { display: none !important; }
}
`

function formatCategory(cat: string | null): string {
  if (!cat) return '—'
  return cat.charAt(0) + cat.slice(1).toLowerCase()
}

export default function ReceiptDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const { data: receipt, isLoading, isError } = useQuery({
    queryKey: ['receipt', id],
    queryFn: () => receiptsApi.get(id!),
    enabled: !!id,
  })

  if (isLoading) {
    return <Typography.Text>Loading...</Typography.Text>
  }

  if (isError || !receipt) {
    return <Typography.Text type="danger">Receipt not found.</Typography.Text>
  }

  const lineItemColumns = [
    {
      title: 'Item',
      key: 'item',
      render: (_: unknown, row: ReceiptLineItem) => (
        <div>
          <div style={{ fontWeight: 500 }}>{row.itemName}</div>
          {row.itemSize && (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              Size: {row.itemSize}
            </Typography.Text>
          )}
          {row.itemCategory && (
            <Typography.Text type="secondary" style={{ fontSize: 12, marginLeft: row.itemSize ? 8 : 0 }}>
              {row.itemSize ? '·' : ''} {formatCategory(row.itemCategory)}
            </Typography.Text>
          )}
          {row.itemDescription && (
            <div>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {row.itemDescription}
              </Typography.Text>
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Qty',
      dataIndex: 'quantity',
      key: 'quantity',
      width: 60,
    },
    {
      title: 'Rate/day',
      dataIndex: 'rateSnapshot',
      key: 'rateSnapshot',
      width: 100,
      render: (v: number) => formatCurrency(v),
    },
    {
      title: 'Deposit',
      dataIndex: 'depositSnapshot',
      key: 'depositSnapshot',
      width: 100,
      render: (v: number) => formatCurrency(v),
    },
    {
      title: 'Rent',
      dataIndex: 'lineRent',
      key: 'lineRent',
      width: 100,
      render: (v: number) => formatCurrency(v),
    },
    {
      title: 'Line Deposit',
      dataIndex: 'lineDeposit',
      key: 'lineDeposit',
      width: 110,
      render: (v: number) => formatCurrency(v),
    },
  ]

  return (
    <>
      <style>{PRINT_STYLES}</style>

      <div style={{ maxWidth: 900 }}>
        {/* Controls — hidden on print */}
        <div className="no-print" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <Button
            type="link"
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate('/receipts')}
            style={{ padding: 0 }}
          >
            Back to Active Rentals
          </Button>
          <Space>
            <Button icon={<PrinterOutlined />} onClick={() => window.print()}>
              Print / Share
            </Button>
            {receipt.status === 'GIVEN' && (
              <Button type="primary" onClick={() => navigate(`/receipts/${receipt.id}/return`)}>
                Process Return
              </Button>
            )}
          </Space>
        </div>

        {/* ── Printable area ── */}
        <div className="receipt-print-area">

          {/* Header */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
            <Typography.Title level={4} style={{ margin: 0 }}>
              {receipt.receiptNumber}
            </Typography.Title>
            <Tag color={receipt.status === 'GIVEN' ? 'blue' : 'green'}>
              {receipt.status}
            </Tag>
          </div>

          {/* Customer & rental details */}
          <Descriptions bordered column={2} size="small" style={{ marginBottom: 20 }}>
            <Descriptions.Item label="Customer">{receipt.customerName}</Descriptions.Item>
            <Descriptions.Item label="Phone">
              <a href={`tel:${receipt.customerPhone}`}>{receipt.customerPhone}</a>
            </Descriptions.Item>
            <Descriptions.Item label="Rental Start">
              {dayjs(receipt.startDatetime).format('DD MMM YYYY, h:mm A')}
            </Descriptions.Item>
            <Descriptions.Item label="Return By">
              <strong>{dayjs(receipt.endDatetime).format('DD MMM YYYY, h:mm A')}</strong>
            </Descriptions.Item>
            <Descriptions.Item label="Rental Days">{receipt.rentalDays} day{receipt.rentalDays !== 1 ? 's' : ''}</Descriptions.Item>
            <Descriptions.Item label="Receipt Date">
              {dayjs(receipt.createdAt).format('DD MMM YYYY, h:mm A')}
            </Descriptions.Item>
          </Descriptions>

          {/* Line items */}
          <Typography.Title level={5} style={{ marginBottom: 10 }}>Items Rented</Typography.Title>
          <Table<ReceiptLineItem>
            dataSource={receipt.lineItems}
            columns={lineItemColumns}
            rowKey="id"
            pagination={false}
            size="small"
            style={{ marginBottom: 20 }}
          />

          {/* Financial summary */}
          <div style={{ display: 'flex', gap: 16, marginBottom: 20, flexWrap: 'wrap' }}>
            {/* Totals */}
            <Descriptions bordered column={1} size="small" style={{ flex: '1 1 280px' }}>
              <Descriptions.Item label="Total Rent">{formatCurrency(receipt.totalRent)}</Descriptions.Item>
              <Descriptions.Item label="Total Deposit">{formatCurrency(receipt.totalDeposit)}</Descriptions.Item>
              <Descriptions.Item label={<strong>Grand Total Paid</strong>}>
                <strong>{formatCurrency(receipt.grandTotal)}</strong>
              </Descriptions.Item>
            </Descriptions>

            {/* Deposit-to-return callout */}
            <div style={{
              flex: '1 1 220px',
              border: '2px solid #1677ff',
              borderRadius: 8,
              padding: '16px 20px',
              background: '#e6f4ff',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'center',
            }}>
              <Typography.Text style={{ fontSize: 13, color: '#555' }}>
                Deposit refundable on return
              </Typography.Text>
              <Typography.Title level={3} style={{ margin: '4px 0 0', color: '#1677ff' }}>
                {formatCurrency(receipt.totalDeposit)}
              </Typography.Title>
              <Typography.Text type="secondary" style={{ fontSize: 12, marginTop: 4 }}>
                Subject to item condition at return
              </Typography.Text>
            </div>
          </div>

          {receipt.notes && (
            <div style={{ marginBottom: 20 }}>
              <Typography.Title level={5}>Notes</Typography.Title>
              <Typography.Text>{receipt.notes}</Typography.Text>
            </div>
          )}

          <Divider />

          {/* Terms & Conditions */}
          <Typography.Title level={5} style={{ marginBottom: 8 }}>
            Terms &amp; Conditions
          </Typography.Title>
          <div style={{ fontSize: 13, color: '#444', lineHeight: 1.7 }}>
            <p style={{ margin: '0 0 6px' }}>
              <strong>Return deadline:</strong> All rented items must be returned by{' '}
              <strong>{dayjs(receipt.endDatetime).format('DD MMM YYYY, h:mm A')}</strong>.
            </p>
            <p style={{ margin: '0 0 6px' }}>
              <strong>Late returns:</strong> Items returned after the due date and time will incur a late fee
              charged per day (or part thereof) of delay. The applicable rate will be communicated at the time
              of return.
            </p>
            <p style={{ margin: '0 0 6px' }}>
              <strong>Damage or loss:</strong> The customer is responsible for any damage, stains, or loss of
              rented items. The cost of repair or full replacement will be charged in addition to the rental
              amount. The deposit will be adjusted against such costs.
            </p>
            <p style={{ margin: '0 0 6px' }}>
              <strong>Deposit refund:</strong> The deposit ({formatCurrency(receipt.totalDeposit)}) will be
              refunded at the time of return, after deducting any applicable late fees or damage charges.
              The rental amount is non-refundable.
            </p>
            <p style={{ margin: 0 }}>
              <strong>Condition of items:</strong> Please handle all rented items with care. Items must be
              returned in the same condition as received.
            </p>
          </div>

          <Divider style={{ marginTop: 16 }} />
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            This receipt is computer-generated. For queries, please contact the shop directly.
          </Typography.Text>
        </div>
      </div>
    </>
  )
}

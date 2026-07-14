import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Descriptions, Divider, Table, Typography } from 'antd'
import dayjs from 'dayjs'
import { publicApi } from '../../api/public'
import type { ReceiptLineItem } from '../../types/receipt'
import { formatCurrency } from '../../utils/currency'

function formatCategory(cat: string | null): string {
  if (!cat) return '—'
  return cat.charAt(0) + cat.slice(1).toLowerCase()
}

export default function PublicReceiptPage() {
  const { shareToken } = useParams<{ shareToken: string }>()

  const { data: receipt, isLoading, isError } = useQuery({
    queryKey: ['public-receipt', shareToken],
    queryFn: () => publicApi.getReceipt(shareToken!),
    enabled: !!shareToken,
  })

  if (isLoading) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Typography.Text>Loading…</Typography.Text>
      </div>
    )
  }

  if (isError || !receipt) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Typography.Text type="danger">Receipt not found.</Typography.Text>
      </div>
    )
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
              {row.itemSize ? '· ' : ''}{formatCategory(row.itemCategory)}
            </Typography.Text>
          )}
        </div>
      ),
    },
    { title: 'Qty', dataIndex: 'quantity', key: 'quantity', width: 55 },
    {
      title: 'Rate/day',
      dataIndex: 'rateSnapshot',
      key: 'rateSnapshot',
      width: 90,
      render: (v: number) => formatCurrency(v),
    },
    {
      title: 'Deposit',
      dataIndex: 'depositSnapshot',
      key: 'depositSnapshot',
      width: 90,
      render: (v: number) => formatCurrency(v),
    },
    {
      title: 'Rent',
      dataIndex: 'lineRent',
      key: 'lineRent',
      width: 90,
      render: (v: number) => formatCurrency(v),
    },
    {
      title: 'Line Deposit',
      dataIndex: 'lineDeposit',
      key: 'lineDeposit',
      width: 100,
      render: (v: number) => formatCurrency(v),
    },
  ]

  return (
    <div style={{ maxWidth: 800, margin: '0 auto', padding: '24px 16px', fontFamily: '"Mulish", system-ui, sans-serif' }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0, color: '#6E0B37' }}>
            Fashion Rental
          </Typography.Title>
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>Rental Receipt</Typography.Text>
        </div>
        <Typography.Title level={5} style={{ margin: 0 }}>
          {receipt.receiptNumber}
        </Typography.Title>
      </div>

      <Divider style={{ margin: '12px 0 20px' }} />

      {/* Customer & rental details */}
      <Descriptions bordered column={{ xs: 1, sm: 2 }} size="small" style={{ marginBottom: 20 }}>
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
        <Descriptions.Item label="Rental Days">
          {receipt.rentalDays} day{receipt.rentalDays !== 1 ? 's' : ''}
        </Descriptions.Item>
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
        scroll={{ x: 'max-content' }}
        style={{ marginBottom: 20 }}
      />

      {/* Financial summary */}
      <div style={{ display: 'flex', gap: 16, marginBottom: 20, flexWrap: 'wrap' }}>
        <Descriptions bordered column={1} size="small" style={{ flex: '1 1 260px' }}>
          <Descriptions.Item label="Total Rent">{formatCurrency(receipt.totalRent)}</Descriptions.Item>
          <Descriptions.Item label="Total Deposit">{formatCurrency(receipt.totalDeposit)}</Descriptions.Item>
          <Descriptions.Item label={<strong>Grand Total Paid</strong>}>
            <strong>{formatCurrency(receipt.grandTotal)}</strong>
          </Descriptions.Item>
        </Descriptions>

        <div style={{
          flex: '1 1 200px',
          border: '2px solid #A81259',
          borderRadius: 8,
          padding: '16px 20px',
          background: '#FBF1F5',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
        }}>
          <Typography.Text style={{ fontSize: 13, color: '#555' }}>Deposit refundable on return</Typography.Text>
          <Typography.Title level={3} style={{ margin: '4px 0 0', color: '#A81259' }}>
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

      {/* Terms */}
      <Typography.Title level={5} style={{ marginBottom: 8 }}>Terms &amp; Conditions</Typography.Title>
      <div style={{ fontSize: 13, color: '#444', lineHeight: 1.7 }}>
        <p style={{ margin: '0 0 6px' }}>
          <strong>Return deadline:</strong> All rented items must be returned by{' '}
          <strong>{dayjs(receipt.endDatetime).format('DD MMM YYYY, h:mm A')}</strong>.
        </p>
        <p style={{ margin: '0 0 6px' }}>
          <strong>Late returns:</strong> Items returned after the due date and time will incur a late fee
          charged per day (or part thereof) of delay.
        </p>
        <p style={{ margin: '0 0 6px' }}>
          <strong>Damage or loss:</strong> The customer is responsible for any damage, stains, or loss of
          rented items. The cost of repair or full replacement will be charged in addition to the rental amount.
        </p>
        <p style={{ margin: 0 }}>
          <strong>Deposit refund:</strong> The deposit ({formatCurrency(receipt.totalDeposit)}) will be
          refunded at the time of return, after deducting any applicable late fees or damage charges.
        </p>
      </div>

      <Divider style={{ marginTop: 16 }} />
      <Typography.Text type="secondary" style={{ fontSize: 11 }}>
        This receipt is computer-generated. For queries, please contact the shop directly.
      </Typography.Text>
    </div>
  )
}

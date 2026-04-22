import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Badge, Button, Empty, Space, Tabs, Tag, Typography } from 'antd'
import { receiptsApi } from '../../api/receipts'
import type { ReceiptSummary } from '../../types/receipt'
import { formatCurrency } from '../../utils/currency'
import dayjs from 'dayjs'

function formatOverdue(hours: number): string {
  if (hours < 1) return `${Math.round(hours * 60)} min overdue`
  if (hours < 24) return `${Math.floor(hours)} hr ${Math.round((hours % 1) * 60)} min overdue`
  const days = Math.floor(hours / 24)
  const hrs = Math.floor(hours % 24)
  return `${days} day${days > 1 ? 's' : ''} ${hrs} hr overdue`
}

function ReceiptCard({ receipt }: { receipt: ReceiptSummary }) {
  const navigate = useNavigate()

  return (
    <div
      style={{
        border: '1px solid #f0f0f0',
        borderLeft: receipt.isOverdue ? '4px solid #ff4d4f' : '1px solid #f0f0f0',
        borderRadius: 8,
        padding: 16,
        marginBottom: 12,
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div style={{ flex: 1 }}>
          <Space wrap>
            <Typography.Text strong>{receipt.receiptNumber}</Typography.Text>
            {receipt.isOverdue && receipt.overdueHours !== null && (
              <Tag color="red">{formatOverdue(receipt.overdueHours)}</Tag>
            )}
          </Space>

          <div style={{ marginTop: 4 }}>
            <Typography.Text>
              <a href={`tel:${receipt.customerPhone}`}>{receipt.customerName}</a>
              {' — '}
              <a href={`tel:${receipt.customerPhone}`}>{receipt.customerPhone}</a>
            </Typography.Text>
          </div>

          <div style={{ marginTop: 4, color: '#666' }}>
            <Typography.Text type="secondary">
              {receipt.itemNames.join(', ')}
            </Typography.Text>
          </div>

          <div style={{ marginTop: 4 }}>
            <Typography.Text type="secondary">
              {dayjs(receipt.startDatetime).format('DD MMM YYYY HH:mm')}
              {' → '}
              {dayjs(receipt.endDatetime).format('DD MMM YYYY HH:mm')}
              {' · '}
              {receipt.rentalDays} day{receipt.rentalDays !== 1 ? 's' : ''}
            </Typography.Text>
          </div>
        </div>

        <div style={{ textAlign: 'right', marginLeft: 16 }}>
          <div>
            <Typography.Text strong>{formatCurrency(receipt.grandTotal)}</Typography.Text>
          </div>
          <div style={{ marginTop: 8 }}>
            <Button size="small" onClick={() => navigate(`/receipts/${receipt.id}`)}>
              Process Return
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}

export default function ReceiptsPage() {
  const { data: receipts = [], isLoading } = useQuery({
    queryKey: ['receipts', 'active'],
    queryFn: () => receiptsApi.list({ status: 'GIVEN' }),
  })

  const overdue = receipts.filter(r => r.isOverdue)

  if (isLoading) {
    return <Typography.Text>Loading...</Typography.Text>
  }

  const tabs = [
    {
      key: 'all',
      label: (
        <Badge count={receipts.length} size="small" color="blue">
          <span style={{ paddingRight: 8 }}>All Active</span>
        </Badge>
      ),
      children: receipts.length === 0
        ? <Empty description="No active rentals" />
        : receipts.map(r => <ReceiptCard key={r.id} receipt={r} />),
    },
    {
      key: 'overdue',
      label: (
        <Badge count={overdue.length} size="small" color="red">
          <span style={{ paddingRight: 8 }}>Overdue</span>
        </Badge>
      ),
      children: overdue.length === 0
        ? <Empty description="No overdue rentals" />
        : overdue.map(r => <ReceiptCard key={r.id} receipt={r} />),
    },
  ]

  return (
    <div>
      <Typography.Title level={4} style={{ marginBottom: 24 }}>
        Active Rentals
      </Typography.Title>
      <Tabs items={tabs} />
    </div>
  )
}

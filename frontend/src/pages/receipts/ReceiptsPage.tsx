import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Badge, Collapse, Empty, Input, Space, Tabs, Tag, Typography } from 'antd'
import { SearchOutlined } from '@ant-design/icons'
import PageHeader from '../../components/common/PageHeader'
import ViewButton, { VIEW_BUTTON_HEIGHT } from '../../components/common/ViewButton'
import { ErrorMessage } from '../../components/common/ErrorMessage'
import { receiptsApi } from '../../api/receipts'
import type { ReceiptSummary } from '../../types/receipt'
import { formatCurrency } from '../../utils/currency'
import { useAuth } from '../../hooks/useAuth'
import { isReceiptCancellable } from '../../utils/receiptCancellation'
import { groupByPickupDate, matchesRentalSearch } from '../../utils/activeRentals'
import CancelReceiptFlow from './CancelReceiptFlow'
import dayjs from 'dayjs'

// Tablet tap target (#168); the card's Cancel and View buttons share it so they line up.
const CARD_ACTION_HEIGHT = VIEW_BUTTON_HEIGHT

function formatOverdue(hours: number): string {
  if (hours < 1) return `${Math.round(hours * 60)} min overdue`
  if (hours < 24) return `${Math.floor(hours)} hr ${Math.round((hours % 1) * 60)} min overdue`
  const days = Math.floor(hours / 24)
  const hrs = Math.floor(hours % 24)
  return `${days} day${days > 1 ? 's' : ''} ${hrs} hr overdue`
}

function ReceiptCard({ receipt }: { receipt: ReceiptSummary }) {
  const navigate = useNavigate()
  const { isOwner } = useAuth()

  return (
    <div
      style={{
        background: '#ffffff',
        border: '1px solid #eed6e0',
        borderLeft: receipt.isOverdue ? '4px solid #ff4d4f' : '1px solid #eed6e0',
        borderRadius: 14,
        boxShadow: '0 6px 20px -12px rgba(110,11,55,0.35)',
        padding: 16,
        marginBottom: 16,
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 12 }}>
        <div style={{ flex: '1 1 220px', minWidth: 0 }}>
          <Space wrap>
            <Typography.Text strong>{receipt.receiptNumber}</Typography.Text>
            {receipt.status === 'CANCELLED' && <Tag color="red">Cancelled</Tag>}
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

        <div style={{ textAlign: 'right' }}>
          <div>
            <Typography.Text type="secondary">Rent </Typography.Text>
            <Typography.Text>{formatCurrency(receipt.totalRent)}</Typography.Text>
          </div>
          <div>
            <Typography.Text type="secondary">Deposit </Typography.Text>
            <Typography.Text>{formatCurrency(receipt.totalDeposit)}</Typography.Text>
          </div>
          {receipt.couponCode && (
            <Tag color="green" style={{ marginTop: 4 }}>
              {receipt.couponCode} −{formatCurrency(receipt.discountAmount)}
            </Tag>
          )}
          <div>
            <Typography.Text type="secondary">Total </Typography.Text>
            <Typography.Text strong>{formatCurrency(receipt.grandTotal)}</Typography.Text>
          </div>
          <div style={{ marginTop: 8 }}>
            <Space wrap align="center" style={{ justifyContent: 'flex-end' }}>
              {isOwner && isReceiptCancellable(receipt) && (
                <CancelReceiptFlow
                  receipt={receipt}
                  trigger="icon"
                  triggerStyle={{ height: CARD_ACTION_HEIGHT, width: CARD_ACTION_HEIGHT }}
                />
              )}
              <ViewButton onClick={() => navigate(`/receipts/${receipt.id}`)} />
            </Space>
          </div>
        </div>
      </div>
    </div>
  )
}

function RentalsByPickupDate({ receipts, isSearching }: { receipts: ReceiptSummary[]; isSearching: boolean }) {
  const groups = groupByPickupDate(receipts)
  const openKeys = groups.filter(g => isSearching || g.isOpenByDefault).map(g => g.dateKey)

  return (
    <Collapse
      // Remount when the visible groups change so a new search re-applies which days start open.
      key={openKeys.join('|') + String(isSearching)}
      defaultActiveKey={openKeys}
      items={groups.map(group => ({
        key: group.dateKey,
        label: (
          <Space>
            <Typography.Text strong>{group.label}</Typography.Text>
            <Badge count={group.receipts.length} color="blue" />
          </Space>
        ),
        children: group.receipts.map(r => <ReceiptCard key={r.id} receipt={r} />),
      }))}
    />
  )
}

export default function ReceiptsPage() {
  const [searchQuery, setSearchQuery] = useState('')
  const isSearching = searchQuery.trim() !== ''

  const { data: allActive = [], isLoading: isActiveLoading, isError: isActiveError } = useQuery({
    queryKey: ['receipts', 'active'],
    queryFn: () => receiptsApi.list({ status: 'GIVEN' }),
  })

  const { data: allCancelled = [], isLoading: isCancelledLoading, isError: isCancelledError } = useQuery({
    queryKey: ['receipts', 'cancelled'],
    queryFn: () => receiptsApi.list({ status: 'CANCELLED' }),
  })

  const receipts = allActive.filter(r => matchesRentalSearch(r, searchQuery))
  const cancelled = allCancelled.filter(r => matchesRentalSearch(r, searchQuery))
  const overdue = receipts.filter(r => r.isOverdue)
  const noMatches = <Empty description={`No rentals match "${searchQuery.trim()}"`} />

  if (isActiveLoading || isCancelledLoading) {
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
      children: isActiveError
        ? <ErrorMessage message="Failed to load active rentals. Please try again." />
        : receipts.length === 0
        ? (isSearching ? noMatches : <Empty description="No active rentals" />)
        : <RentalsByPickupDate receipts={receipts} isSearching={isSearching} />,
    },
    {
      key: 'overdue',
      label: (
        <Badge count={overdue.length} size="small" color="red">
          <span style={{ paddingRight: 8 }}>Overdue</span>
        </Badge>
      ),
      children: isActiveError
        ? <ErrorMessage message="Failed to load overdue rentals. Please try again." />
        : overdue.length === 0
        ? (isSearching ? noMatches : <Empty description="No overdue rentals" />)
        : overdue.map(r => <ReceiptCard key={r.id} receipt={r} />),
    },
    {
      key: 'cancelled',
      label: (
        <Badge count={cancelled.length} size="small" color="red">
          <span style={{ paddingRight: 8 }}>Cancelled (last 7 days)</span>
        </Badge>
      ),
      children: isCancelledError
        ? <ErrorMessage message="Failed to load cancelled receipts. Please try again." />
        : cancelled.length === 0
        ? (isSearching ? noMatches : <Empty description="No receipts cancelled in the last 7 days" />)
        : cancelled.map(r => <ReceiptCard key={r.id} receipt={r} />),
    },
  ]

  return (
    <div>
      <PageHeader label="Rentals" title="Active" accent="Rentals" />
      <Input
        allowClear
        size="large"
        prefix={<SearchOutlined />}
        placeholder="Search by customer or costume name"
        aria-label="Search rentals"
        value={searchQuery}
        onChange={e => setSearchQuery(e.target.value)}
        style={{ marginBottom: 8 }}
      />
      <Tabs items={tabs} />
    </div>
  )
}

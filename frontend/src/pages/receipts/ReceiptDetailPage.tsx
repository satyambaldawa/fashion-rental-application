import { useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  Button,
  Descriptions,
  Table,
  Tag,
  Typography,
} from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { receiptsApi } from '../../api/receipts'
import type { ReceiptLineItem } from '../../types/receipt'
import { formatCurrency } from '../../utils/currency'

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
      dataIndex: 'itemName',
      key: 'itemName',
    },
    {
      title: 'Qty',
      dataIndex: 'quantity',
      key: 'quantity',
    },
    {
      title: 'Rate (snapshot)',
      dataIndex: 'rateSnapshot',
      key: 'rateSnapshot',
      render: (v: number) => formatCurrency(v) + '/day',
    },
    {
      title: 'Deposit (snapshot)',
      dataIndex: 'depositSnapshot',
      key: 'depositSnapshot',
      render: (v: number) => formatCurrency(v),
    },
    {
      title: 'Line Rent',
      dataIndex: 'lineRent',
      key: 'lineRent',
      render: (v: number) => formatCurrency(v),
    },
    {
      title: 'Line Deposit',
      dataIndex: 'lineDeposit',
      key: 'lineDeposit',
      render: (v: number) => formatCurrency(v),
    },
  ]

  return (
    <div style={{ maxWidth: 900 }}>
      <Button
        type="link"
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate('/receipts')}
        style={{ padding: 0, marginBottom: 16 }}
      >
        Back to Active Rentals
      </Button>

      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 24 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {receipt.receiptNumber}
        </Typography.Title>
        <Tag color={receipt.status === 'GIVEN' ? 'blue' : 'green'}>
          {receipt.status}
        </Tag>
      </div>

      <Descriptions bordered column={2} size="small" style={{ marginBottom: 24 }}>
        <Descriptions.Item label="Customer">{receipt.customerName}</Descriptions.Item>
        <Descriptions.Item label="Phone">
          <a href={`tel:${receipt.customerPhone}`}>{receipt.customerPhone}</a>
        </Descriptions.Item>
        <Descriptions.Item label="Start">
          {dayjs(receipt.startDatetime).format('DD MMM YYYY HH:mm')}
        </Descriptions.Item>
        <Descriptions.Item label="End">
          {dayjs(receipt.endDatetime).format('DD MMM YYYY HH:mm')}
        </Descriptions.Item>
        <Descriptions.Item label="Rental Days">{receipt.rentalDays}</Descriptions.Item>
        <Descriptions.Item label="Created">
          {dayjs(receipt.createdAt).format('DD MMM YYYY HH:mm')}
        </Descriptions.Item>
      </Descriptions>

      <Typography.Title level={5} style={{ marginBottom: 12 }}>Line Items</Typography.Title>
      <Table<ReceiptLineItem>
        dataSource={receipt.lineItems}
        columns={lineItemColumns}
        rowKey="id"
        pagination={false}
        size="small"
        style={{ marginBottom: 24 }}
      />

      <Descriptions bordered column={1} size="small" style={{ maxWidth: 400, marginBottom: 24 }}>
        <Descriptions.Item label="Total Rent">{formatCurrency(receipt.totalRent)}</Descriptions.Item>
        <Descriptions.Item label="Total Deposit">{formatCurrency(receipt.totalDeposit)}</Descriptions.Item>
        <Descriptions.Item label={<strong>Grand Total</strong>}>
          <strong>{formatCurrency(receipt.grandTotal)}</strong>
        </Descriptions.Item>
      </Descriptions>

      {receipt.notes && (
        <div style={{ marginBottom: 24 }}>
          <Typography.Title level={5}>Notes</Typography.Title>
          <Typography.Text>{receipt.notes}</Typography.Text>
        </div>
      )}

      {receipt.status === 'GIVEN' && (
        <Button type="primary" onClick={() => navigate(`/receipts/${receipt.id}/return`)}>
          Process Return
        </Button>
      )}
    </div>
  )
}

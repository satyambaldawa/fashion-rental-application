import { useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Space, Spin, Tag, Typography } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { customersApi } from '../../api/customers'
import { AmountDisplay } from '../../components/common/AmountDisplay'
import type { CustomerReceipt, CustomerType } from '../../types/customer'

const CUSTOMER_TYPE_LABELS: Record<CustomerType, string> = {
  STUDENT: 'Student',
  PROFESSIONAL: 'Professional',
  MISC: 'Misc',
}

const CUSTOMER_TYPE_COLORS: Record<CustomerType, string> = {
  STUDENT: 'blue',
  PROFESSIONAL: 'green',
  MISC: 'default',
}

const RECEIPT_STATUS_COLORS: Record<CustomerReceipt['status'], string> = {
  GIVEN: 'orange',
  RETURNED: 'green',
}

function formatDatetime(value: string): string {
  return dayjs(value).format('DD MMM YYYY, h:mm A')
}

export default function CustomerDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const { data: customer, isLoading, isError } = useQuery({
    queryKey: ['customers', id, 'history'],
    queryFn: () => customersApi.getHistory(id!),
    enabled: !!id,
  })

  if (isLoading) {
    return <Spin />
  }

  if (isError || !customer) {
    return (
      <Alert
        type="error"
        message="Failed to load customer history. Please go back and try again."
      />
    )
  }

  return (
    <div style={{ maxWidth: 800 }}>
      <Button
        type="link"
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate('/customers')}
        style={{ padding: 0, marginBottom: 16 }}
      >
        Back to Customers
      </Button>

      <div style={{ marginBottom: 16 }}>
        <Space align="center">
          <Typography.Title level={4} style={{ margin: 0 }}>
            {customer.name}
          </Typography.Title>
          <Tag color={CUSTOMER_TYPE_COLORS[customer.customerType]}>
            {CUSTOMER_TYPE_LABELS[customer.customerType]}
          </Tag>
        </Space>
        <div style={{ color: '#666', fontSize: 13, marginTop: 4 }}>
          {customer.phone}
          {customer.organizationName && ` · ${customer.organizationName}`}
        </div>
      </div>

      {customer.outstandingDeposit > 0 ? (
        <Alert
          type="warning"
          showIcon
          message={
            <span>
              Outstanding Deposit: <AmountDisplay amount={customer.outstandingDeposit} />
            </span>
          }
          style={{ marginBottom: 24 }}
        />
      ) : (
        <div
          style={{
            border: '1px solid #f0f0f0',
            borderRadius: 8,
            padding: '12px 16px',
            marginBottom: 24,
            color: '#666',
          }}
        >
          Outstanding Deposit: <AmountDisplay amount={customer.outstandingDeposit} />
        </div>
      )}

      <Typography.Title level={5} style={{ marginBottom: 12 }}>
        Rental History
      </Typography.Title>

      {customer.receipts.length === 0 && (
        <Typography.Text type="secondary">No rental history yet.</Typography.Text>
      )}

      {customer.receipts.length > 0 && (
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          {customer.receipts.map(receipt => (
            <Card key={receipt.id} size="small">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
                <Typography.Text strong>{receipt.receiptNumber}</Typography.Text>
                <Tag color={RECEIPT_STATUS_COLORS[receipt.status]}>{receipt.status}</Tag>
              </div>

              <div style={{ color: '#666', fontSize: 13, marginBottom: 8 }}>
                {formatDatetime(receipt.startDatetime)} → {formatDatetime(receipt.endDatetime)}
              </div>

              <div style={{ marginBottom: 8 }}>
                {receipt.items.map(item => (
                  <div key={item.itemName}>
                    {item.itemName} ×{item.quantity}
                  </div>
                ))}
              </div>

              <div style={{ display: 'flex', gap: 16, fontSize: 13, flexWrap: 'wrap' }}>
                <span>Rent: <AmountDisplay amount={receipt.totalRent} /></span>
                <span>Deposit: <AmountDisplay amount={receipt.totalDeposit} /></span>
                <span>Total: <AmountDisplay amount={receipt.grandTotal} /></span>
              </div>

              {receipt.invoice && (
                <div style={{ marginTop: 8, paddingTop: 8, borderTop: '1px solid #f0f0f0', fontSize: 13 }}>
                  Invoice: {receipt.invoice.invoiceNumber} ·{' '}
                  {receipt.invoice.transactionType === 'REFUND' ? 'Refunded' : 'Collected'}{' '}
                  <AmountDisplay amount={receipt.invoice.finalAmount} />{' '}
                  <Button
                    type="link"
                    style={{ padding: 0, height: 'auto' }}
                    onClick={() => navigate(`/invoices/${receipt.invoice!.id}`)}
                  >
                    View Invoice
                  </Button>
                </div>
              )}
            </Card>
          ))}
        </Space>
      )}
    </div>
  )
}

import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Descriptions, Divider, Table, Tag, Typography } from 'antd'
import dayjs from 'dayjs'
import { publicApi } from '../../api/public'
import type { InvoiceLineItem } from '../../types/invoice'
import { formatCurrency } from '../../utils/currency'

function formatCategory(cat: string | null): string {
  if (!cat) return ''
  return cat.charAt(0) + cat.slice(1).toLowerCase()
}

export default function PublicInvoicePage() {
  const { shareToken } = useParams<{ shareToken: string }>()

  const { data: invoice, isLoading, isError } = useQuery({
    queryKey: ['public-invoice', shareToken],
    queryFn: () => publicApi.getInvoice(shareToken!),
    enabled: !!shareToken,
  })

  if (isLoading) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Typography.Text>Loading…</Typography.Text>
      </div>
    )
  }

  if (isError || !invoice) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Typography.Text type="danger">Invoice not found.</Typography.Text>
      </div>
    )
  }

  const isRefund = invoice.transactionType === 'REFUND'

  const lineColumns = [
    {
      title: 'Item',
      key: 'item',
      render: (_: unknown, row: InvoiceLineItem) => (
        <div>
          <div style={{ fontWeight: 500 }}>{row.itemName}</div>
          {(row.itemSize || row.itemCategory) && (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {[row.itemSize, row.itemCategory ? formatCategory(row.itemCategory) : null]
                .filter(Boolean).join(' · ')}
            </Typography.Text>
          )}
          {row.isDamaged && (
            <Tag color="red" style={{ marginLeft: 4, fontSize: 11 }}>Damaged</Tag>
          )}
        </div>
      ),
    },
    { title: 'Qty', dataIndex: 'quantityReturned', key: 'qty', width: 55 },
    {
      title: 'Rate/day',
      dataIndex: 'rateSnapshot',
      key: 'rate',
      width: 90,
      render: (v: number) => formatCurrency(v),
    },
    {
      title: 'Late Fee',
      dataIndex: 'lateFee',
      key: 'lateFee',
      width: 90,
      render: (v: number) => v > 0 ? <span style={{ color: '#ff4d4f' }}>{formatCurrency(v)}</span> : '—',
    },
    {
      title: 'Damage',
      key: 'damage',
      width: 110,
      render: (_: unknown, row: InvoiceLineItem) => {
        if (!row.isDamaged || row.damageCost === 0) return '—'
        const label = row.damagePercentage != null ? `${row.damagePercentage}%` : 'Ad hoc'
        return <span style={{ color: '#ff4d4f' }}>{formatCurrency(row.damageCost)} ({label})</span>
      },
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
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>Return Invoice</Typography.Text>
        </div>
        <div style={{ textAlign: 'right' }}>
          <Typography.Title level={5} style={{ margin: 0 }}>{invoice.invoiceNumber}</Typography.Title>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>Receipt: {invoice.receiptNumber}</Typography.Text>
        </div>
      </div>

      <Divider style={{ margin: '12px 0 20px' }} />

      {/* Customer & return details */}
      <Descriptions bordered column={{ xs: 1, sm: 2 }} size="small" style={{ marginBottom: 20 }}>
        <Descriptions.Item label="Customer">{invoice.customerName}</Descriptions.Item>
        <Descriptions.Item label="Phone">
          <a href={`tel:${invoice.customerPhone}`}>{invoice.customerPhone}</a>
        </Descriptions.Item>
        <Descriptions.Item label="Returned On">
          <strong>{dayjs(invoice.returnDatetime).format('DD MMM YYYY, h:mm A')}</strong>
        </Descriptions.Item>
        <Descriptions.Item label="Payment Method">{invoice.paymentMethod}</Descriptions.Item>
        <Descriptions.Item label="Invoice Date">
          {dayjs(invoice.createdAt).format('DD MMM YYYY, h:mm A')}
        </Descriptions.Item>
      </Descriptions>

      {/* Line items */}
      <Typography.Title level={5} style={{ marginBottom: 10 }}>Items Returned</Typography.Title>
      <Table<InvoiceLineItem>
        dataSource={invoice.lineItems}
        columns={lineColumns}
        rowKey="id"
        pagination={false}
        size="small"
        scroll={{ x: 'max-content' }}
        style={{ marginBottom: 20 }}
      />

      {/* Financial summary */}
      <div style={{ display: 'flex', gap: 16, marginBottom: 20, flexWrap: 'wrap' }}>
        <Descriptions bordered column={1} size="small" style={{ flex: '1 1 280px' }}>
          <Descriptions.Item label="Rent Charged">{formatCurrency(invoice.totalRent)}</Descriptions.Item>
          <Descriptions.Item label="Deposit Collected">{formatCurrency(invoice.totalDepositCollected)}</Descriptions.Item>
          {invoice.totalLateFee > 0 && (
            <Descriptions.Item label="Late Fee">
              <span style={{ color: '#ff4d4f' }}>−{formatCurrency(invoice.totalLateFee)}</span>
            </Descriptions.Item>
          )}
          {invoice.totalDamageCost > 0 && (
            <Descriptions.Item label="Damage Cost">
              <span style={{ color: '#ff4d4f' }}>−{formatCurrency(invoice.totalDamageCost)}</span>
            </Descriptions.Item>
          )}
          <Descriptions.Item label="Deposit Returned">{formatCurrency(invoice.depositToReturn)}</Descriptions.Item>
        </Descriptions>

        <div style={{
          flex: '1 1 180px',
          border: `2px solid ${isRefund ? '#52c41a' : '#ff4d4f'}`,
          borderRadius: 8,
          padding: '16px 20px',
          background: isRefund ? '#f6ffed' : '#fff2f0',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          textAlign: 'center',
        }}>
          <Typography.Text style={{ fontSize: 13, color: '#555' }}>
            {isRefund ? 'Amount to Refund' : 'Amount to Collect'}
          </Typography.Text>
          <Typography.Title
            level={2}
            style={{ margin: '4px 0 0', color: isRefund ? '#52c41a' : '#ff4d4f' }}
          >
            {formatCurrency(invoice.finalAmount)}
          </Typography.Title>
          <Typography.Text type="secondary" style={{ fontSize: 12, marginTop: 4 }}>
            {invoice.paymentMethod}
          </Typography.Text>
        </div>
      </div>

      {invoice.damageNotes && (
        <div style={{ marginBottom: 16 }}>
          <Typography.Title level={5} style={{ marginBottom: 4 }}>Damage Notes</Typography.Title>
          <Typography.Text>{invoice.damageNotes}</Typography.Text>
        </div>
      )}

      {invoice.notes && (
        <div style={{ marginBottom: 16 }}>
          <Typography.Title level={5} style={{ marginBottom: 4 }}>Notes</Typography.Title>
          <Typography.Text>{invoice.notes}</Typography.Text>
        </div>
      )}

      <Divider />
      <Typography.Text type="secondary" style={{ fontSize: 11 }}>
        This invoice is computer-generated. Receipt: {invoice.receiptNumber} · Invoice: {invoice.invoiceNumber}
      </Typography.Text>
    </div>
  )
}

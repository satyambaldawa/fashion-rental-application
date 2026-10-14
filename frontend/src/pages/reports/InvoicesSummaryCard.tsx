import { Card, Descriptions, Typography } from 'antd'
import { formatCurrency } from '../../utils/currency'
import type { InvoicesReport } from '../../types/reports'

export default function InvoicesSummaryCard({ report }: { report: InvoicesReport }) {
  return (
    <Card style={{ marginTop: 20 }}>
      <Typography.Text type="secondary">
        {report.invoiceCount} invoice{report.invoiceCount !== 1 ? 's' : ''}
      </Typography.Text>
      <Descriptions column={1} size="small" style={{ marginTop: 8 }}>
        <Descriptions.Item label="Total items rented">{report.totalItems}</Descriptions.Item>
        <Descriptions.Item label="Rent">{formatCurrency(report.totalRent)}</Descriptions.Item>
        <Descriptions.Item label="Discounts">−{formatCurrency(report.totalDiscount)}</Descriptions.Item>
        <Descriptions.Item label="Late fees">{formatCurrency(report.totalLateFee)}</Descriptions.Item>
        <Descriptions.Item label="Damage">{formatCurrency(report.totalDamage)}</Descriptions.Item>
      </Descriptions>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 8 }}>
        <Typography.Text strong style={{ fontSize: 16 }}>Total revenue</Typography.Text>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {formatCurrency(report.totalRevenue)}
        </Typography.Title>
      </div>
      <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8, fontSize: 12 }}>
        Revenue is counted on the invoice date. Rent is paid at checkout, so this can differ
        from Monthly and Daily Revenue.
      </Typography.Text>
    </Card>
  )
}

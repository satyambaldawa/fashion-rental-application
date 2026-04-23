import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  Alert,
  Badge,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Row,
  Space,
  Spin,
  Statistic,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import { PhoneOutlined } from '@ant-design/icons'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import dayjs, { type Dayjs } from 'dayjs'
import { reportsApi } from '../../api/reports'
import { formatCurrency } from '../../utils/currency'
import type { DailyRevenueSummary, OutstandingDepositItem, OverdueRentalItem } from '../../types/reports'

function formatOverdueDuration(hours: number): string {
  if (hours < 24) return `${Math.floor(hours)} hr${Math.floor(hours) !== 1 ? 's' : ''}`
  const days = Math.floor(hours / 24)
  const remainingHours = Math.floor(hours % 24)
  return remainingHours > 0 ? `${days}d ${remainingHours}h` : `${days} day${days !== 1 ? 's' : ''}`
}

function DailyRevenueTab() {
  const [selectedDate, setSelectedDate] = useState<Dayjs>(dayjs())
  const dateStr = selectedDate.format('YYYY-MM-DD')

  const { data, isLoading, isError } = useQuery({
    queryKey: ['reports', 'daily-revenue', dateStr],
    queryFn: () => reportsApi.getDailyRevenue(dateStr),
  })

  return (
    <div style={{ maxWidth: 600 }}>
      <Space style={{ marginBottom: 20 }}>
        <Typography.Text strong>Date:</Typography.Text>
        <DatePicker
          value={selectedDate}
          onChange={v => setSelectedDate(v ?? dayjs())}
          format="DD MMM YYYY"
          allowClear={false}
        />
      </Space>

      {isLoading && <Spin />}
      {isError && <Alert type="error" message="Failed to load daily revenue." />}

      {data && (
        <Card title={`Summary — ${selectedDate.format('DD MMM YYYY')}`}>
          <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
            New Rentals ({data.newReceiptsCount} receipt{data.newReceiptsCount !== 1 ? 's' : ''})
          </Typography.Text>
          <Descriptions column={1} size="small" bordered style={{ marginBottom: 16 }}>
            <Descriptions.Item label="Rent collected">{formatCurrency(data.rentCollected)}</Descriptions.Item>
            <Descriptions.Item label="Deposits taken on">{formatCurrency(data.depositsCollected)}</Descriptions.Item>
          </Descriptions>

          <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
            Returns Processed ({data.returnsProcessedCount} invoice{data.returnsProcessedCount !== 1 ? 's' : ''})
          </Typography.Text>
          <Descriptions column={1} size="small" bordered style={{ marginBottom: 16 }}>
            <Descriptions.Item label="Deposits refunded">{formatCurrency(data.depositsRefunded)}</Descriptions.Item>
            {data.collectedFromCustomers > 0 && (
              <Descriptions.Item label="Collected from customers">{formatCurrency(data.collectedFromCustomers)}</Descriptions.Item>
            )}
            <Descriptions.Item label="Late fee income">{formatCurrency(data.lateFeeIncome)}</Descriptions.Item>
            <Descriptions.Item label="Damage income">{formatCurrency(data.damageIncome)}</Descriptions.Item>
          </Descriptions>

          <div style={{
            padding: '14px 20px',
            background: data.netFlow >= 0 ? '#f6ffed' : '#fff2f0',
            border: `2px solid ${data.netFlow >= 0 ? '#52c41a' : '#ff4d4f'}`,
            borderRadius: 8,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}>
            <Typography.Text strong style={{ fontSize: 16 }}>Net Cash Flow</Typography.Text>
            <Typography.Title
              level={4}
              style={{ margin: 0, color: data.netFlow >= 0 ? '#52c41a' : '#ff4d4f' }}
            >
              {formatCurrency(data.netFlow)}
            </Typography.Title>
          </div>
        </Card>
      )}
    </div>
  )
}

function OutstandingDepositsTab() {
  const navigate = useNavigate()
  const { data, isLoading, isError } = useQuery({
    queryKey: ['reports', 'outstanding-deposits'],
    queryFn: reportsApi.getOutstandingDeposits,
  })

  if (isLoading) return <Spin />
  if (isError) return <Alert type="error" message="Failed to load outstanding deposits." />
  if (!data) return null

  return (
    <div style={{ maxWidth: 700 }}>
      <div style={{
        padding: '12px 20px',
        background: '#e6f4ff',
        border: '1px solid #91caff',
        borderRadius: 8,
        marginBottom: 20,
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
      }}>
        <Typography.Text strong>Total Outstanding Deposit</Typography.Text>
        <Typography.Title level={4} style={{ margin: 0, color: '#1677ff' }}>
          {formatCurrency(data.totalOutstanding)}
        </Typography.Title>
      </div>

      {data.items.length === 0 && (
        <Typography.Text type="secondary">No active rentals with outstanding deposits.</Typography.Text>
      )}

      {data.items.map((item: OutstandingDepositItem) => (
        <Card key={item.receiptId} size="small" style={{ marginBottom: 12 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <Typography.Text strong style={{ marginRight: 8 }}>{item.receiptNumber}</Typography.Text>
              <Typography.Text>{item.customerName}</Typography.Text>
              <Typography.Text type="secondary" style={{ marginLeft: 8 }}>
                <PhoneOutlined /> {item.customerPhone}
              </Typography.Text>
            </div>
            <Tag color="blue">{formatCurrency(item.deposit)}</Tag>
          </div>
          <div style={{ marginTop: 6, color: '#595959', fontSize: 13 }}>
            {item.itemNames.join(', ')}
          </div>
          <div style={{ marginTop: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              Due: {dayjs(item.endDatetime).format('DD MMM YYYY, h:mm A')}
              {' · '}
              {item.daysSinceRented} day{item.daysSinceRented !== 1 ? 's' : ''} since rented
            </Typography.Text>
            <Button size="small" onClick={() => navigate(`/receipts/${item.receiptId}/return`)}>
              Process Return
            </Button>
          </div>
        </Card>
      ))}
    </div>
  )
}

function OverdueRentalsTab() {
  const navigate = useNavigate()
  const { data, isLoading, isError } = useQuery({
    queryKey: ['reports', 'overdue-rentals'],
    queryFn: reportsApi.getOverdueRentals,
  })

  if (isLoading) return <Spin />
  if (isError) return <Alert type="error" message="Failed to load overdue rentals." />
  if (!data) return null

  return (
    <div style={{ maxWidth: 700 }}>
      {data.overdueCount > 0 ? (
        <div style={{
          padding: '10px 16px',
          background: '#fff2f0',
          border: '1px solid #ffccc7',
          borderRadius: 8,
          marginBottom: 20,
        }}>
          <Typography.Text strong style={{ color: '#ff4d4f' }}>
            {data.overdueCount} overdue rental{data.overdueCount !== 1 ? 's' : ''}
          </Typography.Text>
        </div>
      ) : (
        <Alert type="success" message="No overdue rentals." style={{ marginBottom: 20 }} />
      )}

      {data.items.map((item: OverdueRentalItem) => (
        <Card key={item.receiptId} size="small" style={{ marginBottom: 12, borderColor: '#ffccc7' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <Typography.Text strong style={{ marginRight: 8 }}>{item.receiptNumber}</Typography.Text>
              <Typography.Text>{item.customerName}</Typography.Text>
              <Typography.Text type="secondary" style={{ marginLeft: 8 }}>
                <PhoneOutlined /> {item.customerPhone}
              </Typography.Text>
            </div>
            <Badge color="red" text={formatOverdueDuration(item.overdueHours) + ' overdue'} />
          </div>
          <div style={{ marginTop: 6, color: '#595959', fontSize: 13 }}>
            {item.itemNames.join(', ')}
          </div>
          <div style={{ marginTop: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              Was due: {dayjs(item.endDatetime).format('DD MMM YYYY, h:mm A')}
            </Typography.Text>
            <Button size="small" type="primary" danger onClick={() => navigate(`/receipts/${item.receiptId}/return`)}>
              Process Return
            </Button>
          </div>
        </Card>
      ))}
    </div>
  )
}

function MonthlyRevenueTab() {
  const [selectedMonth, setSelectedMonth] = useState<Dayjs>(dayjs())
  const year = selectedMonth.year()
  const month = selectedMonth.month() + 1 // dayjs months are 0-indexed

  const { data, isLoading, isError } = useQuery({
    queryKey: ['reports', 'monthly-revenue', year, month],
    queryFn: () => reportsApi.getMonthlyRevenue(year, month),
  })

  const chartData = data?.dailyBreakdown.map((d: DailyRevenueSummary) => ({
    day: dayjs(d.date).format('D'),
    'Rent': d.rentCollected,
    'Deposit In': d.depositsCollected,
    'Late Fee + Damage': d.lateFeeIncome + d.damageIncome,
    'Total': d.netFlow,
  })) ?? []

  return (
    <div>
      <Space style={{ marginBottom: 20 }}>
        <Typography.Text strong>Month:</Typography.Text>
        <DatePicker
          picker="month"
          value={selectedMonth}
          onChange={v => setSelectedMonth(v ?? dayjs())}
          format="MMM YYYY"
          allowClear={false}
        />
      </Space>

      {isLoading && <Spin />}
      {isError && <Alert type="error" message="Failed to load monthly revenue." />}

      {data && (
        <>
          {/* Summary stat cards */}
          <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
            <Col xs={12} sm={8} md={6}>
              <Card size="small">
                <Statistic
                  title="Rent Collected"
                  value={data.totalRentCollected}
                  formatter={v => formatCurrency(v as number)}
                  valueStyle={{ color: '#52c41a', fontSize: 18 }}
                />
              </Card>
            </Col>
            <Col xs={12} sm={8} md={6}>
              <Card size="small">
                <Statistic
                  title="Deposits Collected"
                  value={data.totalDepositsCollected}
                  formatter={v => formatCurrency(v as number)}
                  valueStyle={{ color: '#1677ff', fontSize: 18 }}
                />
              </Card>
            </Col>
            <Col xs={12} sm={8} md={6}>
              <Card size="small">
                <Statistic
                  title="Deposits Refunded"
                  value={data.totalDepositsRefunded}
                  formatter={v => formatCurrency(v as number)}
                  valueStyle={{ color: '#ff4d4f', fontSize: 18 }}
                />
              </Card>
            </Col>
            <Col xs={12} sm={8} md={6}>
              <Card size="small">
                <Statistic
                  title="Late Fee Income"
                  value={data.totalLateFeeIncome}
                  formatter={v => formatCurrency(v as number)}
                  valueStyle={{ color: '#fa8c16', fontSize: 18 }}
                />
              </Card>
            </Col>
            <Col xs={12} sm={8} md={6}>
              <Card size="small">
                <Statistic
                  title="Damage Income"
                  value={data.totalDamageIncome}
                  formatter={v => formatCurrency(v as number)}
                  valueStyle={{ color: '#fa8c16', fontSize: 18 }}
                />
              </Card>
            </Col>
            <Col xs={12} sm={8} md={6}>
              <Card size="small" style={{ borderColor: data.totalNetFlow >= 0 ? '#b7eb8f' : '#ffa39e' }}>
                <Statistic
                  title="Net Cash Flow"
                  value={data.totalNetFlow}
                  formatter={v => formatCurrency(v as number)}
                  valueStyle={{ color: data.totalNetFlow >= 0 ? '#52c41a' : '#ff4d4f', fontSize: 18 }}
                />
              </Card>
            </Col>
          </Row>

          {/* Daily bar chart */}
          <Card title={`Daily Breakdown — ${selectedMonth.format('MMMM YYYY')}`}>
            <ResponsiveContainer width="100%" height={320}>
              <BarChart data={chartData} margin={{ top: 4, right: 16, left: 16, bottom: 4 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="day" tick={{ fontSize: 11 }} />
                <YAxis
                  tickFormatter={v => `₹${(v / 1000).toFixed(0)}k`}
                  tick={{ fontSize: 11 }}
                  width={52}
                />
                <Tooltip formatter={(value) => formatCurrency(Number(value))} />
                <Legend />
                <Bar dataKey="Rent" stackId="a" fill="#52c41a" radius={[0, 0, 0, 0]} />
                <Bar dataKey="Deposit In" stackId="a" fill="#1677ff" />
                <Bar dataKey="Late Fee + Damage" stackId="a" fill="#fa8c16" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </>
      )}
    </div>
  )
}

export default function ReportsPage() {
  return (
    <div>
      <Typography.Title level={4} style={{ marginBottom: 20 }}>Reports</Typography.Title>
      <Tabs
        defaultActiveKey="monthly-revenue"
        items={[
          { key: 'monthly-revenue', label: 'Monthly Revenue', children: <MonthlyRevenueTab /> },
          { key: 'daily-revenue', label: 'Daily Revenue', children: <DailyRevenueTab /> },
          { key: 'outstanding-deposits', label: 'Outstanding Deposits', children: <OutstandingDepositsTab /> },
          { key: 'overdue-rentals', label: 'Overdue Rentals', children: <OverdueRentalsTab /> },
        ]}
      />
    </div>
  )
}

import { useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import {
  Alert,
  Badge,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Empty,
  InputNumber,
  Row,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import { PhoneOutlined } from '@ant-design/icons'
import PageHeader from '../../components/common/PageHeader'
import ViewButton from '../../components/common/ViewButton'
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
import axios from 'axios'
import { useDebounce } from '../../hooks/useDebounce'
import { reportsApi } from '../../api/reports'
import { formatCurrency } from '../../utils/currency'
import type {
  CouponDiscountSummary,
  DailyRevenueSummary,
  InvoiceListItem,
  OutstandingDepositItem,
  OverdueRentalItem,
} from '../../types/reports'
import type { InvoiceBackLink } from '../../types/invoice'

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
            {data.totalDiscountsGiven > 0 && (
              <Descriptions.Item label="Discounts given">
                <span style={{ color: '#52c41a' }}>−{formatCurrency(data.totalDiscountsGiven)}</span>
              </Descriptions.Item>
            )}
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

          {data.cancellationsCount > 0 && (
            <>
              <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
                Cancellations ({data.cancellationsCount} receipt{data.cancellationsCount !== 1 ? 's' : ''})
              </Typography.Text>
              <Descriptions column={1} size="small" bordered style={{ marginBottom: 16 }}>
                <Descriptions.Item label="Refunded on cancellation">
                  <span style={{ color: '#ff4d4f' }}>−{formatCurrency(data.cancellationRefunds)}</span>
                </Descriptions.Item>
              </Descriptions>
            </>
          )}

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
        <Typography.Title level={4} style={{ margin: 0, color: '#C2185B' }}>
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
    'Cancellation Refunds': d.cancellationRefunds,
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
                  valueStyle={{ color: '#C2185B', fontSize: 18 }}
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
              <Card size="small">
                <Statistic
                  title="Discounts Given"
                  value={data.totalDiscountsGiven}
                  formatter={v => formatCurrency(v as number)}
                  valueStyle={{ color: '#52c41a', fontSize: 18 }}
                />
              </Card>
            </Col>
            <Col xs={12} sm={8} md={6}>
              <Card size="small">
                <Statistic
                  title="Cancellation Refunds"
                  value={data.totalCancellationRefunds}
                  formatter={v => formatCurrency(v as number)}
                  valueStyle={{ color: '#ff4d4f', fontSize: 18 }}
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
                <Bar dataKey="Deposit In" stackId="a" fill="#C2185B" />
                <Bar dataKey="Late Fee + Damage" stackId="a" fill="#fa8c16" radius={[4, 4, 0, 0]} />
                <Bar dataKey="Cancellation Refunds" fill="#ff4d4f" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </>
      )}
    </div>
  )
}

function DiscountsGivenTab() {
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().startOf('month'), dayjs()])
  const [from, to] = range

  const { data, isLoading, isError } = useQuery({
    queryKey: ['reports', 'discounts-given', from.format('YYYY-MM-DD'), to.format('YYYY-MM-DD')],
    queryFn: () => reportsApi.getDiscountsGiven(from.format('YYYY-MM-DD'), to.format('YYYY-MM-DD')),
  })

  const columns = [
    { title: 'Coupon', dataIndex: 'couponCode', key: 'couponCode' },
    { title: 'Times Applied', dataIndex: 'timesApplied', key: 'timesApplied' },
    {
      title: 'Total Discount',
      dataIndex: 'totalDiscount',
      key: 'totalDiscount',
      render: (v: number) => formatCurrency(v),
    },
  ]

  return (
    <div style={{ maxWidth: 700 }}>
      <Space style={{ marginBottom: 20 }}>
        <Typography.Text strong>Range:</Typography.Text>
        <DatePicker.RangePicker
          value={range}
          onChange={v => v && v[0] && v[1] && setRange([v[0], v[1]])}
          format="DD MMM YYYY"
          allowClear={false}
        />
      </Space>

      {isLoading && <Spin />}
      {isError && <Alert type="error" message="Failed to load discounts given." />}

      {data && (
        <>
          <div style={{
            padding: '14px 20px',
            background: '#f6ffed',
            border: '1px solid #b7eb8f',
            borderRadius: 8,
            marginBottom: 20,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}>
            <div>
              <Typography.Text strong style={{ fontSize: 16 }}>Total Discount Given</Typography.Text>
              <div>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {data.receiptsWithCoupon} receipt{data.receiptsWithCoupon !== 1 ? 's' : ''} with a coupon applied
                </Typography.Text>
              </div>
            </div>
            <Typography.Title level={4} style={{ margin: 0, color: '#52c41a' }}>
              {formatCurrency(data.totalDiscountGiven)}
            </Typography.Title>
          </div>

          {data.byCoupon.length === 0 ? (
            <Typography.Text type="secondary">No coupons applied in this range.</Typography.Text>
          ) : (
            <Table<CouponDiscountSummary>
              columns={columns}
              dataSource={data.byCoupon}
              rowKey="couponCode"
              size="small"
              pagination={false}
            />
          )}
        </>
      )}
    </div>
  )
}

// Mirrors ReportingService.MAX_INVOICE_REPORT_DAYS; the backend rejects longer ranges.
const MAX_INVOICE_RANGE_DAYS = 366
const INVOICES_PAGE_SIZE = 20
const MIN_REVENUE_DEBOUNCE_MS = 400
const DEFAULT_INVOICE_RANGE_DAYS = 7
const URL_DATE_FORMAT = 'YYYY-MM-DD'
const URL_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

function parseUrlDate(value: string | null, fallback: Dayjs): Dayjs {
  return value && URL_DATE_PATTERN.test(value) && dayjs(value).isValid() ? dayjs(value) : fallback
}

function parseUrlPositiveInt(value: string | null): number | null {
  const parsed = Number(value)
  return value !== null && Number.isInteger(parsed) && parsed >= 0 ? parsed : null
}

// Filters and page live in the URL so the browser's back button, and the invoice page's
// "Back to Invoices", return to exactly this view.
function InvoicesTab() {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()

  const today = dayjs()
  const rangeFrom = parseUrlDate(searchParams.get('from'), today.subtract(DEFAULT_INVOICE_RANGE_DAYS - 1, 'day'))
  const rangeTo = parseUrlDate(searchParams.get('to'), today)
  const minRevenue = parseUrlPositiveInt(searchParams.get('min'))
  const page = Math.max(parseUrlPositiveInt(searchParams.get('page')) ?? 1, 1)
  const debouncedMinRevenue = useDebounce(minRevenue, MIN_REVENUE_DEBOUNCE_MS)

  const updateParams = (changes: Record<string, string | null>) => {
    setSearchParams(current => {
      const next = new URLSearchParams(current)
      Object.entries(changes).forEach(([key, value]) => (value === null ? next.delete(key) : next.set(key, value)))
      return next
    }, { replace: true })
  }

  const from = rangeFrom.format(URL_DATE_FORMAT)
  const to = rangeTo.format(URL_DATE_FORMAT)
  const { data, isLoading, isFetching, isError, error } = useQuery({
    queryKey: ['reports', 'invoices', from, to, debouncedMinRevenue, page],
    queryFn: () => reportsApi.getInvoices({
      from, to, minRevenue: debouncedMinRevenue ?? undefined, page: page - 1, size: INVOICES_PAGE_SIZE,
    }),
    placeholderData: keepPreviousData,
  })

  const openInvoice = (invoiceId: string) => {
    const backLink: InvoiceBackLink = { backTo: location.pathname + location.search, backLabel: 'Back to Invoices' }
    navigate(`/invoices/${invoiceId}`, { state: backLink })
  }

  const columns = [
    { title: 'Invoice', dataIndex: 'invoiceNumber', key: 'invoiceNumber' },
    {
      title: 'Invoiced on',
      dataIndex: 'invoiceDate',
      key: 'invoiceDate',
      render: (v: string) => dayjs(v).format('DD MMM YYYY'),
    },
    { title: 'Customer', dataIndex: 'customerName', key: 'customerName' },
    { title: 'Receipt', dataIndex: 'receiptNumber', key: 'receiptNumber' },
    { title: 'Items', dataIndex: 'items', key: 'items', align: 'right' as const },
    {
      title: 'Revenue',
      dataIndex: 'revenue',
      key: 'revenue',
      align: 'right' as const,
      render: (v: number) => <Typography.Text strong>{formatCurrency(v)}</Typography.Text>,
    },
    {
      title: '',
      key: 'view',
      align: 'right' as const,
      render: (_: unknown, record: InvoiceListItem) => (
        <ViewButton ariaLabel={`View ${record.invoiceNumber}`} onClick={() => openInvoice(record.invoiceId)} />
      ),
    },
  ]

  return (
    <div style={{ maxWidth: 1000 }}>
      <Space wrap style={{ marginBottom: 20 }}>
        <Typography.Text strong>Range:</Typography.Text>
        <DatePicker.RangePicker
          value={[rangeFrom, rangeTo]}
          onChange={v => v && v[0] && v[1] && updateParams({
            from: v[0].format(URL_DATE_FORMAT), to: v[1].format(URL_DATE_FORMAT), page: null,
          })}
          disabledDate={(current, { from: start }) =>
            !!start && Math.abs(current.diff(start, 'day')) >= MAX_INVOICE_RANGE_DAYS}
          format="DD MMM YYYY"
          allowClear={false}
        />
        <InputNumber
          aria-label="Min revenue"
          placeholder="Min revenue"
          prefix="₹"
          min={0}
          precision={0}
          value={minRevenue}
          onChange={v => updateParams({ min: v === null ? null : String(v), page: null })}
          style={{ width: 160 }}
        />
        {minRevenue !== null && <Button onClick={() => updateParams({ min: null, page: null })}>Clear</Button>}
      </Space>

      {isLoading && <Spin />}
      {isError && (
        <Alert
          type="error"
          message={(axios.isAxiosError(error) && error.response?.data?.error) || 'Failed to load invoices.'}
        />
      )}

      {data && (
        <>
          {data.invoiceCount === 0 ? (
            <Empty description="No invoices for these filters" />
          ) : (
            <Table<InvoiceListItem>
              columns={columns}
              dataSource={data.invoices}
              rowKey="invoiceId"
              loading={isFetching}
              scroll={{ x: 'max-content' }}
              pagination={{
                current: page,
                pageSize: INVOICES_PAGE_SIZE,
                total: data.invoiceCount,
                showSizeChanger: false,
                hideOnSinglePage: true,
                onChange: next => updateParams({ page: next === 1 ? null : String(next) }),
              }}
            />
          )}

          <Card style={{ marginTop: 20 }}>
            <Typography.Text type="secondary">
              {data.invoiceCount} invoice{data.invoiceCount !== 1 ? 's' : ''}
            </Typography.Text>
            <Descriptions column={1} size="small" style={{ marginTop: 8 }}>
              <Descriptions.Item label="Total items rented">{data.totalItems}</Descriptions.Item>
              <Descriptions.Item label="Rent">{formatCurrency(data.totalRent)}</Descriptions.Item>
              <Descriptions.Item label="Discounts">−{formatCurrency(data.totalDiscount)}</Descriptions.Item>
              <Descriptions.Item label="Late fees">{formatCurrency(data.totalLateFee)}</Descriptions.Item>
              <Descriptions.Item label="Damage">{formatCurrency(data.totalDamage)}</Descriptions.Item>
            </Descriptions>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 8 }}>
              <Typography.Text strong style={{ fontSize: 16 }}>Total revenue</Typography.Text>
              <Typography.Title level={4} style={{ margin: 0 }}>
                {formatCurrency(data.totalRevenue)}
              </Typography.Title>
            </div>
            <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8, fontSize: 12 }}>
              Revenue is counted on the invoice date. Rent is paid at checkout, so this can differ
              from Monthly and Daily Revenue.
            </Typography.Text>
          </Card>
        </>
      )}
    </div>
  )
}

const DEFAULT_REPORT_TAB = 'monthly-revenue'

export default function ReportsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const activeTab = searchParams.get('tab') ?? DEFAULT_REPORT_TAB

  return (
    <div>
      <PageHeader label="Reports" title="Revenue &" accent="Insights" />
      <Tabs
        activeKey={activeTab}
        onChange={key => setSearchParams({ tab: key }, { replace: true })}
        items={[
          { key: 'monthly-revenue', label: 'Monthly Revenue', children: <MonthlyRevenueTab /> },
          { key: 'daily-revenue', label: 'Daily Revenue', children: <DailyRevenueTab /> },
          { key: 'discounts-given', label: 'Discounts Given', children: <DiscountsGivenTab /> },
          { key: 'invoices', label: 'Invoices', children: <InvoicesTab /> },
          { key: 'outstanding-deposits', label: 'Outstanding Deposits', children: <OutstandingDepositsTab /> },
          { key: 'overdue-rentals', label: 'Overdue Rentals', children: <OverdueRentalsTab /> },
        ]}
      />
    </div>
  )
}

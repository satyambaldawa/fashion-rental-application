import { useLocation, useNavigate } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Alert, Button, DatePicker, Empty, InputNumber, Space, Spin, Table, Typography } from 'antd'
import dayjs from 'dayjs'
import axios from 'axios'
import ViewButton from '../../components/common/ViewButton'
import { reportsApi } from '../../api/reports'
import { useDebounce } from '../../hooks/useDebounce'
import { formatCurrency } from '../../utils/currency'
import type { InvoiceListItem } from '../../types/reports'
import type { InvoiceBackLink } from '../../types/invoice'
import InvoicesSummaryCard from './InvoicesSummaryCard'
import { URL_DATE_FORMAT, useInvoicesReportFilters } from './useInvoicesReportFilters'

// Mirrors ReportingService.MAX_INVOICE_REPORT_DAYS; the backend rejects longer ranges.
const MAX_INVOICE_RANGE_DAYS = 366
const INVOICES_PAGE_SIZE = 20
const MIN_REVENUE_DEBOUNCE_MS = 400

export default function InvoicesTab() {
  const navigate = useNavigate()
  const location = useLocation()
  const { rangeFrom, rangeTo, minRevenue, page, setRange, setMinRevenue, setPage } = useInvoicesReportFilters()
  const debouncedMinRevenue = useDebounce(minRevenue, MIN_REVENUE_DEBOUNCE_MS)

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
          onChange={v => v && v[0] && v[1] && setRange(v[0], v[1])}
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
          onChange={setMinRevenue}
          style={{ width: 160 }}
        />
        {minRevenue !== null && <Button onClick={() => setMinRevenue(null)}>Clear</Button>}
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
                onChange: setPage,
              }}
            />
          )}
          <InvoicesSummaryCard report={data} />
        </>
      )}
    </div>
  )
}

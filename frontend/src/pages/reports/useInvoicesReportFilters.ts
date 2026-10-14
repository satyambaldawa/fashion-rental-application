import { useSearchParams } from 'react-router-dom'
import dayjs, { type Dayjs } from 'dayjs'

const DEFAULT_INVOICE_RANGE_DAYS = 7
const FIRST_PAGE = 1
export const URL_DATE_FORMAT = 'YYYY-MM-DD'
const URL_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/
const INVOICE_URL_PARAM = { from: 'from', to: 'to', minRevenue: 'min', page: 'page' } as const

function parseUrlDate(value: string | null, fallback: Dayjs): Dayjs {
  return value && URL_DATE_PATTERN.test(value) && dayjs(value).isValid() ? dayjs(value) : fallback
}

function parseUrlNonNegativeInt(value: string | null): number | null {
  const parsed = Number(value)
  return value !== null && Number.isInteger(parsed) && parsed >= 0 ? parsed : null
}

export interface InvoicesReportFilters {
  rangeFrom: Dayjs
  rangeTo: Dayjs
  minRevenue: number | null
  /** 1-based, as shown in the pager. */
  page: number
  setRange: (from: Dayjs, to: Dayjs) => void
  setMinRevenue: (minRevenue: number | null) => void
  setPage: (page: number) => void
}

// Filters and page live in the URL so the browser's back button, and the invoice page's
// "Back to Invoices", return to exactly this view. Changing a filter returns to the first page.
export function useInvoicesReportFilters(): InvoicesReportFilters {
  const [searchParams, setSearchParams] = useSearchParams()

  const today = dayjs()
  const rangeFrom = parseUrlDate(
    searchParams.get(INVOICE_URL_PARAM.from), today.subtract(DEFAULT_INVOICE_RANGE_DAYS - 1, 'day'))
  const rangeTo = parseUrlDate(searchParams.get(INVOICE_URL_PARAM.to), today)
  const minRevenue = parseUrlNonNegativeInt(searchParams.get(INVOICE_URL_PARAM.minRevenue))
  const page = Math.max(parseUrlNonNegativeInt(searchParams.get(INVOICE_URL_PARAM.page)) ?? FIRST_PAGE, FIRST_PAGE)

  const updateParams = (changes: Record<string, string | null>) => {
    setSearchParams(current => {
      const next = new URLSearchParams(current)
      Object.entries(changes).forEach(([key, value]) => (value === null ? next.delete(key) : next.set(key, value)))
      return next
    }, { replace: true })
  }

  return {
    rangeFrom,
    rangeTo,
    minRevenue,
    page,
    setRange: (from, to) => updateParams({
      [INVOICE_URL_PARAM.from]: from.format(URL_DATE_FORMAT),
      [INVOICE_URL_PARAM.to]: to.format(URL_DATE_FORMAT),
      [INVOICE_URL_PARAM.page]: null,
    }),
    setMinRevenue: value => updateParams({
      [INVOICE_URL_PARAM.minRevenue]: value === null ? null : String(value),
      [INVOICE_URL_PARAM.page]: null,
    }),
    setPage: next => updateParams({ [INVOICE_URL_PARAM.page]: next === FIRST_PAGE ? null : String(next) }),
  }
}

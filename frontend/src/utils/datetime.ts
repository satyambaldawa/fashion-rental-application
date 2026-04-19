import dayjs from 'dayjs'
import timezone from 'dayjs/plugin/timezone'
import utc from 'dayjs/plugin/utc'

dayjs.extend(utc)
dayjs.extend(timezone)

const IST = 'Asia/Kolkata'

export function formatDatetime(iso: string): string {
  return dayjs(iso).tz(IST).format('DD MMM YYYY, hh:mm A')
}

export function formatDate(iso: string): string {
  return dayjs(iso).tz(IST).format('DD MMM YYYY')
}

export function toApiDatetime(date: dayjs.Dayjs): string {
  return date.tz(IST).toISOString()
}

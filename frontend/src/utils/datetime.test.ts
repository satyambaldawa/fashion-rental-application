import { describe, it, expect } from 'vitest'
import { formatDatetime, formatDate, toApiDatetime } from './datetime'
import dayjs from 'dayjs'
import timezone from 'dayjs/plugin/timezone'
import utc from 'dayjs/plugin/utc'
dayjs.extend(utc)
dayjs.extend(timezone)

const IST = 'Asia/Kolkata'

describe('formatDatetime', () => {
  it('should format an ISO string to readable IST datetime', () => {
    const iso = '2026-04-19T10:00:00+05:30'
    expect(formatDatetime(iso)).toBe('19 Apr 2026, 10:00 AM')
  })

  it('should handle UTC timestamps and convert to IST', () => {
    // 04:30 UTC = 10:00 IST
    const iso = '2026-04-19T04:30:00Z'
    expect(formatDatetime(iso)).toBe('19 Apr 2026, 10:00 AM')
  })
})

describe('formatDate', () => {
  it('should format an ISO string to date only', () => {
    const iso = '2026-04-19T10:00:00+05:30'
    expect(formatDate(iso)).toBe('19 Apr 2026')
  })
})

describe('toApiDatetime', () => {
  it('should convert a dayjs object to ISO 8601 string', () => {
    const date = dayjs.tz('2026-04-19 10:00:00', IST)
    const result = toApiDatetime(date)
    expect(result).toBeTruthy()
    expect(result).toContain('2026-04-19')
  })
})

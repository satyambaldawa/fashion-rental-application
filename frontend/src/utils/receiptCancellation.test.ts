import { describe, it, expect } from 'vitest'
import dayjs from 'dayjs'
import { isReceiptCancellable } from './receiptCancellation'

describe('isReceiptCancellable', () => {
  const now = dayjs('2026-04-18T12:00:00+05:30')

  it('is true for a GIVEN receipt ending 13 hours from now', () => {
    const receipt = { status: 'GIVEN' as const, endDatetime: now.add(13, 'hour').toISOString() }

    expect(isReceiptCancellable(receipt, now)).toBe(true)
  })

  it('is false for a GIVEN receipt ending exactly 12 hours from now', () => {
    const receipt = { status: 'GIVEN' as const, endDatetime: now.add(12, 'hour').toISOString() }

    expect(isReceiptCancellable(receipt, now)).toBe(false)
  })

  it('is true for a GIVEN receipt ending just over 12 hours from now (12h 1m)', () => {
    const receipt = { status: 'GIVEN' as const, endDatetime: now.add(12, 'hour').add(1, 'minute').toISOString() }

    expect(isReceiptCancellable(receipt, now)).toBe(true)
  })

  it('is false for a GIVEN receipt ending 11 hours from now', () => {
    const receipt = { status: 'GIVEN' as const, endDatetime: now.add(11, 'hour').toISOString() }

    expect(isReceiptCancellable(receipt, now)).toBe(false)
  })

  it('is false for an overdue GIVEN receipt', () => {
    const receipt = { status: 'GIVEN' as const, endDatetime: now.subtract(1, 'hour').toISOString() }

    expect(isReceiptCancellable(receipt, now)).toBe(false)
  })

  it('is false for a RETURNED receipt ending 3 days from now', () => {
    const receipt = { status: 'RETURNED' as const, endDatetime: now.add(3, 'day').toISOString() }

    expect(isReceiptCancellable(receipt, now)).toBe(false)
  })

  it('is false for a CANCELLED receipt ending 3 days from now', () => {
    const receipt = { status: 'CANCELLED' as const, endDatetime: now.add(3, 'day').toISOString() }

    expect(isReceiptCancellable(receipt, now)).toBe(false)
  })
})

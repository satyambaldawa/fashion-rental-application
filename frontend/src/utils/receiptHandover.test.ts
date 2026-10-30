import { describe, expect, it } from 'vitest'
import dayjs from 'dayjs'
import { isGivenUndoable } from './receiptHandover'
import type { ReceiptStatus } from '../types/receipt'

describe('isGivenUndoable', () => {
  const now = dayjs('2026-04-18T12:00:00+05:30')
  const FUTURE_BOOKED_START = '2026-04-19T10:00:00+05:30'
  const PAST_BOOKED_START = '2026-04-17T10:00:00+05:30'

  it('allows undo for a GIVEN receipt whose booked start is still in the future', () => {
    expect(isGivenUndoable({ status: 'GIVEN', startDatetime: FUTURE_BOOKED_START }, now)).toBe(true)
  })

  it('does not allow undo once the booked start has passed', () => {
    expect(isGivenUndoable({ status: 'GIVEN', startDatetime: PAST_BOOKED_START }, now)).toBe(false)
  })

  it('does not allow undo when the booked start is exactly now', () => {
    expect(isGivenUndoable({ status: 'GIVEN', startDatetime: now.toISOString() }, now)).toBe(false)
  })

  it.each<ReceiptStatus>(['GENERATED', 'RETURNED', 'CANCELLED'])(
    'does not allow undo for a %s receipt',
    (status) => {
      expect(isGivenUndoable({ status, startDatetime: FUTURE_BOOKED_START }, now)).toBe(false)
    }
  )
})

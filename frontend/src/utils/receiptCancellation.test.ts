import { describe, expect, it } from 'vitest'
import { isReceiptCancellable } from './receiptCancellation'
import type { ReceiptStatus } from '../types/receipt'

describe('isReceiptCancellable', () => {
  it('should_allow_cancelling_a_booking_whose_dress_has_not_been_given_out', () => {
    expect(isReceiptCancellable({ status: 'GENERATED' })).toBe(true)
  })

  it.each<ReceiptStatus>(['GIVEN', 'RETURNED', 'CANCELLED'])(
    'should_not_allow_cancelling_a_%s_receipt',
    (status) => {
      expect(isReceiptCancellable({ status })).toBe(false)
    }
  )
})

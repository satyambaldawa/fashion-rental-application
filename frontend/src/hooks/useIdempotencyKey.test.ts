import { describe, it, expect } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useIdempotencyKey } from './useIdempotencyKey'

type CheckoutRequestFixture = { customerId: string; startDatetime: string; couponCode: string | null }

const request: CheckoutRequestFixture = { customerId: 'cust-1', startDatetime: '2026-04-18T10:00:00', couponCode: null }

describe('useIdempotencyKey', () => {
  it('keeps the same key across re-renders of an identical request (a retry)', () => {
    const { result, rerender } = renderHook(({ req }) => useIdempotencyKey(req), {
      initialProps: { req: request },
    })
    const firstKey = result.current.key

    rerender({ req: { ...request } })

    expect(result.current.key).toBe(firstKey)
  })

  it('issues a new key when only the dates change', () => {
    const { result, rerender } = renderHook(({ req }) => useIdempotencyKey(req), {
      initialProps: { req: request },
    })
    const firstKey = result.current.key

    rerender({ req: { ...request, startDatetime: '2026-04-19T10:00:00' } })

    expect(result.current.key).not.toBe(firstKey)
  })

  it('issues a new key when only the coupon changes', () => {
    const { result, rerender } = renderHook(({ req }) => useIdempotencyKey(req), {
      initialProps: { req: request },
    })
    const firstKey = result.current.key

    rerender({ req: { ...request, couponCode: 'SAVE20' } })

    expect(result.current.key).not.toBe(firstKey)
  })

  it('issues a new key after rotate even when the request is identical (a deliberate repeat after success)', () => {
    const { result } = renderHook(() => useIdempotencyKey(request))
    const firstKey = result.current.key

    act(() => result.current.rotate())

    expect(result.current.key).not.toBe(firstKey)
  })

  it('issues a different key on a fresh mount', () => {
    const first = renderHook(() => useIdempotencyKey(request))
    const second = renderHook(() => useIdempotencyKey(request))

    expect(second.result.current.key).not.toBe(first.result.current.key)
  })
})

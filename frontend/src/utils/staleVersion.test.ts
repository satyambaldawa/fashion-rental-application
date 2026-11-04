import { describe, it, expect } from 'vitest'
import { isMissingVersionError, isStaleVersionError, STALE_VERSION_MESSAGE } from './staleVersion'

const apiError = (status: number, error: string) => ({ response: { status, data: { error } } })

describe('isStaleVersionError', () => {
  it('recognises a 409 carrying the stale-version message', () => {
    expect(isStaleVersionError(apiError(409, STALE_VERSION_MESSAGE))).toBe(true)
  })

  it('does not treat an unrelated 409 as a stale version', () => {
    expect(isStaleVersionError(apiError(409, 'A customer with phone 9811122233 already exists'))).toBe(false)
  })

  it('does not treat the stale message on another status as a stale version', () => {
    expect(isStaleVersionError(apiError(500, STALE_VERSION_MESSAGE))).toBe(false)
  })

  it('returns false for errors without a response', () => {
    expect(isStaleVersionError(new Error('Network Error'))).toBe(false)
    expect(isStaleVersionError(null)).toBe(false)
  })
})

describe('isMissingVersionError', () => {
  it('recognises a 400 whose message names the version field', () => {
    expect(isMissingVersionError(apiError(400, 'version: must not be null'))).toBe(true)
  })

  it('does not match an unrelated 400', () => {
    expect(isMissingVersionError(apiError(400, 'name: must not be blank'))).toBe(false)
  })

  it('does not match the same text on another status', () => {
    expect(isMissingVersionError(apiError(500, 'version: must not be null'))).toBe(false)
  })
})

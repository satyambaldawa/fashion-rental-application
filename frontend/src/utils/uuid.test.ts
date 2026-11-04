import { describe, it, expect, afterEach, vi } from 'vitest'
import { generateUuidV4 } from './uuid'

const UUID_V4_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

describe('generateUuidV4', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('returns a valid v4 UUID even when crypto.randomUUID is unavailable (plain HTTP)', () => {
    const getRandomValues = crypto.getRandomValues.bind(crypto)
    vi.stubGlobal('crypto', { getRandomValues })

    const uuid = generateUuidV4()

    expect(uuid).toMatch(UUID_V4_PATTERN)
  })

  it('returns a different value on every call when crypto.randomUUID is unavailable', () => {
    const getRandomValues = crypto.getRandomValues.bind(crypto)
    vi.stubGlobal('crypto', { getRandomValues })

    const uuids = new Set(Array.from({ length: 50 }, () => generateUuidV4()))

    expect(uuids.size).toBe(50)
  })

  it('returns a valid v4 UUID when crypto.randomUUID is available', () => {
    expect(generateUuidV4()).toMatch(UUID_V4_PATTERN)
  })
})

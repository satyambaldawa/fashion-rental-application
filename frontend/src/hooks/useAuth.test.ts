import { describe, it, expect, beforeEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useAuth } from './useAuth'
import { useAuthStore } from '../store/authStore'

function jwtWithRole(role: string): string {
  const encode = (obj: object) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${encode({ alg: 'HS256' })}.${encode({ sub: 'user', role, exp: 9999999999 })}.signature`
}

describe('useAuth', () => {
  beforeEach(() => useAuthStore.setState({ token: null, role: null }))

  it('is unauthenticated without a token', () => {
    const { result } = renderHook(() => useAuth())
    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.isOwner).toBe(false)
  })

  it('decodes the OWNER role from a valid token', () => {
    useAuthStore.setState({ token: jwtWithRole('OWNER'), role: 'OWNER' })
    const { result } = renderHook(() => useAuth())
    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.isOwner).toBe(true)
  })

  it('falls back to EXECUTIVE on a malformed token', () => {
    useAuthStore.setState({ token: 'not-a-jwt', role: null })
    const { result } = renderHook(() => useAuth())
    expect(result.current.role).toBe('EXECUTIVE')
    expect(result.current.isOwner).toBe(false)
  })
})

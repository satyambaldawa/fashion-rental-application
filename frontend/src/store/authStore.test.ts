import { describe, it, expect, beforeEach } from 'vitest'
import { useAuthStore } from './authStore'

describe('authStore', () => {
  beforeEach(() => useAuthStore.setState({ token: null, role: null }))

  it('setAuth stores the token and role', () => {
    useAuthStore.getState().setAuth('jwt-token', 'OWNER')
    expect(useAuthStore.getState().token).toBe('jwt-token')
    expect(useAuthStore.getState().role).toBe('OWNER')
  })

  it('clearToken resets both token and role', () => {
    useAuthStore.getState().setAuth('jwt-token', 'EXECUTIVE')
    useAuthStore.getState().clearToken()
    expect(useAuthStore.getState().token).toBeNull()
    expect(useAuthStore.getState().role).toBeNull()
  })
})

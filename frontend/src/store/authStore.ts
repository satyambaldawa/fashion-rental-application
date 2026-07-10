import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { UserRole } from '../types/auth'

interface AuthState {
  token: string | null
  role: UserRole | null
  setAuth: (token: string, role: UserRole) => void
  clearToken: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      role: null,
      setAuth: (token, role) => set({ token, role }),
      clearToken: () => set({ token: null, role: null }),
    }),
    { name: 'auth-storage' }
  )
)

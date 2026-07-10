import { useAuthStore } from '../store/authStore'
import type { UserRole } from '../types/auth'

export function useAuth() {
  const token = useAuthStore((s) => s.token)
  const role = (useAuthStore((s) => s.role) ?? 'EXECUTIVE') as UserRole

  return {
    isAuthenticated: !!token,
    role,
    isOwner: role === 'OWNER',
  }
}

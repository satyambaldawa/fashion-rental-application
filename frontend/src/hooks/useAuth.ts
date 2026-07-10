import { jwtDecode } from 'jwt-decode'
import { useAuthStore } from '../store/authStore'
import type { UserRole } from '../types/auth'

interface JwtPayload {
  sub: string
  role: UserRole
  exp: number
}

export function useAuth() {
  const token = useAuthStore((s) => s.token)

  let role: UserRole = 'EXECUTIVE'
  if (token) {
    try {
      role = jwtDecode<JwtPayload>(token).role
    } catch {
      role = 'EXECUTIVE'
    }
  }

  return {
    isAuthenticated: !!token,
    role,
    isOwner: role === 'OWNER',
  }
}

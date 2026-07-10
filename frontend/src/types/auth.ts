export type UserRole = 'OWNER' | 'EXECUTIVE'

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  role: UserRole
}

export interface CreateUserRequest {
  username: string
  password: string
  role: UserRole
}

export interface UpdateUserRequest {
  role?: UserRole
  password?: string
}

export interface UserRecord {
  id: string
  username: string
  role: UserRole
  createdAt: string
}

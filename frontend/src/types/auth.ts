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

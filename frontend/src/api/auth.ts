import client from './client'
import type { ApiResponse } from '../types/api'
import type { CreateUserRequest, LoginRequest, LoginResponse } from '../types/auth'

export const authApi = {
  login: (data: LoginRequest) =>
    client.post<ApiResponse<LoginResponse>>('/auth/login', data).then(r => r.data.data!),

  createUser: (data: CreateUserRequest) =>
    client.post<ApiResponse<null>>('/auth/users', data).then(r => r.data),
}

// Keep backward-compatible named export used by LoginPage
export async function login(request: LoginRequest): Promise<LoginResponse> {
  return authApi.login(request)
}

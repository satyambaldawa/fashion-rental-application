import client from './client'
import type { ApiResponse } from '../types/api'
import type { CreateUserRequest, UpdateUserRequest, UserRecord, LoginRequest, LoginResponse } from '../types/auth'

export const authApi = {
  login: (data: LoginRequest) =>
    client.post<ApiResponse<LoginResponse>>('/auth/login', data).then(r => r.data.data!),

  listUsers: () =>
    client.get<ApiResponse<UserRecord[]>>('/auth/users').then(r => r.data.data!),

  createUser: (data: CreateUserRequest) =>
    client.post<ApiResponse<UserRecord>>('/auth/users', data).then(r => r.data.data!),

  updateUser: (id: string, data: UpdateUserRequest) =>
    client.put<ApiResponse<UserRecord>>(`/auth/users/${id}`, data).then(r => r.data.data!),

  deleteUser: (id: string) =>
    client.delete<ApiResponse<null>>(`/auth/users/${id}`).then(r => r.data),
}

// Keep backward-compatible named export used by LoginPage
export async function login(request: LoginRequest): Promise<LoginResponse> {
  return authApi.login(request)
}

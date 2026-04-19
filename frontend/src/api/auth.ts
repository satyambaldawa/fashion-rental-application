import client from './client'
import type { ApiResponse } from '../types/api'
import type { LoginRequest, LoginResponse } from '../types/auth'

export async function login(request: LoginRequest): Promise<string> {
  const res = await client.post<ApiResponse<LoginResponse>>('/auth/login', request)
  return res.data.data!.token
}

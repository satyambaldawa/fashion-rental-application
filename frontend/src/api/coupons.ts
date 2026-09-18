import client from './client'
import type { ApiResponse } from '../types/api'
import type { Coupon, CreateCouponRequest, UpdateCouponRequest } from '../types/coupons'

export const couponsApi = {
  list: (includeInactive?: boolean): Promise<Coupon[]> =>
    client.get<ApiResponse<Coupon[]>>('/config/coupons', { params: includeInactive ? { includeInactive } : undefined })
      .then(r => r.data.data!),

  get: (id: string): Promise<Coupon> =>
    client.get<ApiResponse<Coupon>>(`/config/coupons/${id}`).then(r => r.data.data!),

  create: (data: CreateCouponRequest): Promise<Coupon> =>
    client.post<ApiResponse<Coupon>>('/config/coupons', data).then(r => r.data.data!),

  update: (id: string, data: UpdateCouponRequest): Promise<Coupon> =>
    client.put<ApiResponse<Coupon>>(`/config/coupons/${id}`, data).then(r => r.data.data!),

  setStatus: (id: string, isActive: boolean): Promise<Coupon> =>
    client.patch<ApiResponse<Coupon>>(`/config/coupons/${id}/status`, { isActive }).then(r => r.data.data!),
}

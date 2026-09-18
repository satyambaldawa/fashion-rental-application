export type DiscountType = 'PERCENT' | 'FIXED'

export interface Coupon {
  id: string
  code: string
  discountType: DiscountType
  value: number
  minSubtotal: number | null
  validFrom: string
  validTo: string
  usageLimit: number | null
  timesUsed: number
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateCouponRequest {
  code: string
  discountType: DiscountType
  value: number
  minSubtotal: number | null
  validFrom: string
  validTo: string
  usageLimit: number | null
}

export type UpdateCouponRequest = Omit<CreateCouponRequest, 'code'>

export interface SetCouponStatusRequest {
  isActive: boolean
}

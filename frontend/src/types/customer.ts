export type CustomerType = 'STUDENT' | 'PROFESSIONAL' | 'MISC'

export interface Customer {
  id: string
  name: string
  phone: string
  address: string | null
  customerType: CustomerType
  organizationName: string | null
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface CustomerSummary {
  id: string
  name: string
  phone: string
  address: string | null
  customerType: CustomerType
  organizationName: string | null
  activeRentalsCount: number
}

export interface CreateCustomerRequest {
  name: string
  phone: string
  address?: string
  customerType: CustomerType
  organizationName?: string
}

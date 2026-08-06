import client from './client'
import type { ApiResponse } from '../types/api'
import type { Customer, CustomerSummary, CreateCustomerRequest, UpdateCustomerRequest } from '../types/customer'

export const customersApi = {
  search: (params: { phone?: string; name?: string }): Promise<CustomerSummary[]> =>
    client.get<ApiResponse<CustomerSummary[]>>('/customers', { params }).then(r => r.data.data!),

  create: (data: CreateCustomerRequest): Promise<Customer> =>
    client.post<ApiResponse<Customer>>('/customers', data).then(r => r.data.data!),

  get: (id: string): Promise<Customer> =>
    client.get<ApiResponse<Customer>>(`/customers/${id}`).then(r => r.data.data!),

  update: (id: string, data: UpdateCustomerRequest): Promise<Customer> =>
    client.put<ApiResponse<Customer>>(`/customers/${id}`, data).then(r => r.data.data!),
}

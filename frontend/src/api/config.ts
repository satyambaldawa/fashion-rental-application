import client from './client'
import type { ApiResponse } from '../types/api'
import type { LateFeeRule, UpdateLateFeeRulesRequest } from '../types/config'

export async function getLateFeeRules(): Promise<LateFeeRule[]> {
  const res = await client.get<ApiResponse<LateFeeRule[]>>('/config/late-fee-rules')
  return res.data.data ?? []
}

export async function updateLateFeeRules(request: UpdateLateFeeRulesRequest): Promise<LateFeeRule[]> {
  const res = await client.put<ApiResponse<LateFeeRule[]>>('/config/late-fee-rules', request)
  return res.data.data ?? []
}

export interface LateFeeRule {
  id: string
  durationFromHours: number
  durationToHours: number | null
  penaltyMultiplier: number
  sortOrder: number
  isActive: boolean
  label: string
  version: number
}

export interface LateFeeRuleItem {
  id: string | null
  durationFromHours: number
  durationToHours: number | null
  penaltyMultiplier: number
  sortOrder: number
  isActive: boolean
  version: number | null
}

export interface UpdateLateFeeRulesRequest {
  rules: LateFeeRuleItem[]
}

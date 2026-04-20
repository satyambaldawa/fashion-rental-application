export interface LateFeeRule {
  id: string
  durationFromHours: number
  durationToHours: number | null
  penaltyMultiplier: number
  sortOrder: number
  isActive: boolean
  label: string
}

export interface LateFeeRuleItem {
  id: string | null
  durationFromHours: number
  durationToHours: number | null
  penaltyMultiplier: number
  sortOrder: number
  isActive: boolean
}

export interface UpdateLateFeeRulesRequest {
  rules: LateFeeRuleItem[]
}

export type ItemCategory = 'COSTUME' | 'ACCESSORIES' | 'PAGDI' | 'DRESS' | 'ORNAMENTS'
export type ItemType = 'INDIVIDUAL' | 'PACKAGE'

export interface ItemPhoto {
  id: string
  url: string
  thumbnailUrl: string
  sortOrder: number
}

export interface ItemSummary {
  id: string
  name: string
  category: ItemCategory
  size: string | null
  rate: number
  deposit: number
  totalQuantity: number
  availableQuantity: number
  isAvailable: boolean
  thumbnailUrl: string | null
  photoUrls: string[]
}

export interface ItemDetail extends ItemSummary {
  itemType: ItemType
  description: string | null
  notes: string | null
  quantity: number
  isActive: boolean
  photos: ItemPhoto[]
  createdAt: string
  updatedAt: string
}

export interface CreateItemRequest {
  name: string
  category: ItemCategory
  size?: string
  description?: string
  rate: number
  deposit: number
  quantity: number
  notes?: string
}

export interface AvailabilityResult {
  itemId: string
  availableQuantity: number
  isAvailable: boolean
}

export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

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
  itemType: ItemType
  size: string | null
  description: string | null
  rate: number
  deposit: number
  totalQuantity: number
  availableQuantity: number
  isAvailable: boolean
  thumbnailUrl: string | null
  photoUrls: string[]
  componentNames: string[] | null   // null for INDIVIDUAL, ["Name ×qty"] for PACKAGE
}

export interface ItemDetail extends ItemSummary {
  itemType: ItemType
  description: string | null
  notes: string | null
  // Internal purchase tracking — visible to staff only
  purchaseRate: number | null
  vendorName: string | null
  quantity: number
  isActive: boolean
  // Non-null only when itemType = 'PACKAGE'
  components: PackageComponent[] | null
  photos: ItemPhoto[]
  createdAt: string
  updatedAt: string
}

export interface PackageComponentRequest {
  componentItemId: string
  quantity: number
}

export interface PackageComponent {
  componentItemId: string
  componentItemName: string
  componentItemCategory: ItemCategory
  componentItemSize: string | null
  componentItemDescription: string | null
  componentItemPhotos: ItemPhoto[]
  quantity: number
}

export interface CreateItemRequest {
  name: string
  category: ItemCategory
  itemType: ItemType
  size?: string
  description?: string
  rate: number
  deposit: number
  quantity: number
  notes?: string
  // Internal purchase tracking — not shown to customers
  purchaseRate?: number
  vendorName?: string
  // Required when itemType = 'PACKAGE'
  components?: PackageComponentRequest[]
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

import type { ItemCategory } from './inventory'

export interface GalleryImage {
  id: string
  category: ItemCategory
  imageUrl: string
  thumbnailUrl: string
  caption: string | null
  isActive: boolean
}

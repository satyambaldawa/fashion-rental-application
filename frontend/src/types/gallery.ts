import type { ItemCategory } from './inventory'

export interface GalleryImage {
  id: string
  category: ItemCategory
  imageUrl: string
  thumbnailUrl: string
  caption: string | null
  isActive: boolean
}

// Partial update — only the provided fields are applied server-side.
// To clear a caption send an empty string; `null`/omitted leaves it unchanged.
export interface UpdateGalleryImageRequest {
  caption?: string
  isActive?: boolean
}

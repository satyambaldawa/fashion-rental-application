import { publicClient } from './public'
import type { ApiResponse } from '../types/api'
import type { GalleryImage } from '../types/gallery'
import type { ItemCategory } from '../types/inventory'

export const galleryApi = {
  list: (category?: ItemCategory): Promise<GalleryImage[]> =>
    publicClient
      .get<ApiResponse<GalleryImage[]>>('/gallery', { params: category ? { category } : {} })
      .then(r => r.data.data!),
}

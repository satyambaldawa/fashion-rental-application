import client from './client'
import { publicClient } from './public'
import type { ApiResponse } from '../types/api'
import type { GalleryImage, UpdateGalleryImageRequest } from '../types/gallery'
import type { ItemCategory } from '../types/inventory'

export const galleryApi = {
  list: (category?: ItemCategory): Promise<GalleryImage[]> =>
    publicClient
      .get<ApiResponse<GalleryImage[]>>('/gallery', { params: category ? { category } : {} })
      .then(r => r.data.data!),
}

// Owner-only admin operations. These hit the authenticated /api/gallery endpoints
// (not /api/public/gallery) and so include inactive images in listings.
export const galleryAdminApi = {
  list: (category?: ItemCategory): Promise<GalleryImage[]> =>
    client
      .get<ApiResponse<GalleryImage[]>>('/gallery', { params: category ? { category } : {} })
      .then(r => r.data.data!),

  upload: (category: ItemCategory, files: File[]): Promise<GalleryImage[]> => {
    const form = new FormData()
    files.forEach(file => form.append('files', file))
    form.append('category', category)
    // Override the client's default application/json: without this axios sees a JSON
    // content-type and serialises the FormData to JSON instead of multipart. The
    // browser fills in the multipart boundary once the JSON header is out of the way.
    return client
      .post<ApiResponse<GalleryImage[]>>('/gallery', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then(r => r.data.data!)
  },

  update: (id: string, request: UpdateGalleryImageRequest): Promise<GalleryImage> =>
    client
      .patch<ApiResponse<GalleryImage>>(`/gallery/${id}`, request)
      .then(r => r.data.data!),

  remove: (id: string): Promise<void> =>
    client.delete(`/gallery/${id}`).then(() => undefined),
}

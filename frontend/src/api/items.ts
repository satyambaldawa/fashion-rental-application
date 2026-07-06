import client from './client'
import type { ApiResponse } from '../types/api'
import type {
  AvailabilityResult,
  CreateItemRequest,
  ItemDetail,
  ItemPhoto,
  ItemSummary,
  PageResult,
  UpdateItemRequest,
} from '../types/inventory'

export const itemsApi = {
  list: (params: { page?: number; size?: number; search?: string; category?: string; itemSize?: string; startDatetime?: string; endDatetime?: string }) =>
    client.get<ApiResponse<PageResult<ItemSummary>>>('/items', { params }).then(r => r.data.data!),

  get: (id: string) =>
    client.get<ApiResponse<ItemDetail>>(`/items/${id}`).then(r => r.data.data!),

  create: (data: CreateItemRequest) =>
    client.post<ApiResponse<ItemDetail>>('/items', data).then(r => r.data.data!),

  update: (id: string, data: UpdateItemRequest) =>
    client.put<ApiResponse<ItemDetail>>(`/items/${id}`, data).then(r => r.data.data!),

  clone: (id: string) =>
    client.post<ApiResponse<ItemDetail>>(`/items/${id}/clone`).then(r => r.data.data!),

  delete: (id: string) =>
    client.delete<ApiResponse<null>>(`/items/${id}`).then(r => r.data),

  checkAvailability: (id: string, startDatetime: string, endDatetime: string) =>
    client.get<ApiResponse<AvailabilityResult>>(`/items/${id}/availability`, {
      params: { startDatetime, endDatetime }
    }).then(r => r.data.data!),

  uploadPhoto: (itemId: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return client.post<ApiResponse<ItemPhoto>>(`/items/${itemId}/photos`, form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }).then(r => r.data.data!)
  },

  deletePhoto: (itemId: string, photoId: string) =>
    client.delete(`/items/${itemId}/photos/${photoId}`),

  reorderPhotos: (itemId: string, photos: { id: string; sortOrder: number }[]) =>
    client.patch(`/items/${itemId}/photos/order`, { photos }),
}

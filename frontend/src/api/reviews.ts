import client from './client'
import { publicClient } from './public'
import type { ApiResponse, PageResult } from '../types/api'
import type {
  AdminReview,
  ListModerationReviewsParams,
  ListPublicReviewsParams,
  PublicReview,
  ReviewStatus,
  SubmitReviewRequest,
  SubmitReviewResponse,
  UpdateReviewStatusRequest,
} from '../types/review'

export const reviewsApi = {
  listPublic: (params: ListPublicReviewsParams): Promise<PageResult<PublicReview>> =>
    publicClient
      .get<ApiResponse<PageResult<PublicReview>>>('/reviews', { params })
      .then(r => r.data.data!),

  submit: (request: SubmitReviewRequest, images: File[]): Promise<SubmitReviewResponse> => {
    const form = new FormData()
    form.append('review', new Blob([JSON.stringify(request)], { type: 'application/json' }))
    images.forEach(image => form.append('images', image))
    // publicClient defaults Content-Type to application/json, which makes axios serialise the
    // FormData as JSON. Overriding it to multipart/form-data (the repo's existing convention —
    // see galleryAdminApi.upload / itemsApi.uploadPhoto) lets the browser supply the boundary.
    return publicClient
      .post<ApiResponse<SubmitReviewResponse>>('/reviews', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then(r => r.data.data!)
  },
}

// Owner-only moderation operations on the authenticated /api/reviews endpoints
// (not /api/public/reviews) — these expose the reviewer's phone number.
export const reviewsAdminApi = {
  listForModeration: (params: ListModerationReviewsParams): Promise<PageResult<AdminReview>> =>
    client
      .get<ApiResponse<PageResult<AdminReview>>>('/reviews', { params })
      .then(r => r.data.data!),

  updateStatus: (id: string, status: ReviewStatus): Promise<AdminReview> =>
    client
      .patch<ApiResponse<AdminReview>>(`/reviews/${id}/status`, { status } satisfies UpdateReviewStatusRequest)
      .then(r => r.data.data!),

  remove: (id: string): Promise<void> =>
    client.delete(`/reviews/${id}`).then(() => undefined),
}

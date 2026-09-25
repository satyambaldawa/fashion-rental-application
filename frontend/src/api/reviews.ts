import { publicClient } from './public'
import type { ApiResponse, PageResult } from '../types/api'
import type {
  ListPublicReviewsParams,
  PublicReview,
  SubmitReviewRequest,
  SubmitReviewResponse,
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

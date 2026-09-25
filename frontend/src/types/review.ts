export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type ReviewSort = 'NEWEST' | 'HIGHEST_RATED'

export interface ReviewImage {
  id: string
  url: string
  thumbnailUrl: string
}

// Mirrors PublicReviewResponse. Deliberately has no phone field — the public API never returns one.
export interface PublicReview {
  id: string
  reviewerName: string
  itemDescription: string
  rating: number
  reviewText: string
  createdAt: string
  images: ReviewImage[]
}

export interface SubmitReviewRequest {
  reviewerName: string
  phone: string
  itemDescription: string
  rating: number
  reviewText: string
}

export interface SubmitReviewResponse {
  id: string
  status: ReviewStatus
}

export interface ListPublicReviewsParams {
  sort: ReviewSort
  page: number
}

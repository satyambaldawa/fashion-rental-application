export interface ApiResponse<T> {
  success: boolean
  data: T | null
  error: string | null
}

export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

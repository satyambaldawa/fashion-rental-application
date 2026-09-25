import { useState } from 'react'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { Card, Empty, Image, Pagination, Rate, Select, Space, Typography } from 'antd'
import dayjs from 'dayjs'
import { reviewsApi } from '../../api/reviews'
import PageHeader from '../../components/common/PageHeader'
import { LoadingSpinner } from '../../components/common/LoadingSpinner'
import { ErrorMessage } from '../../components/common/ErrorMessage'
import type { PublicReview, ReviewSort } from '../../types/review'

const { Text, Paragraph } = Typography

// Must match the server's fixed page size; used only to size the Pagination control.
const PAGE_SIZE = 10
const REVIEW_DATE_FORMAT = 'D MMM YYYY'

const SORT_OPTIONS: { value: ReviewSort; label: string }[] = [
  { value: 'NEWEST', label: 'Newest' },
  { value: 'HIGHEST_RATED', label: 'Highest rated' },
]

function ReviewCard({ review }: { review: PublicReview }) {
  return (
    <Card>
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        <Space wrap>
          <Text strong>{review.reviewerName}</Text>
          <Rate disabled value={review.rating} />
          <Text type="secondary">{dayjs(review.createdAt).format(REVIEW_DATE_FORMAT)}</Text>
        </Space>
        <Text type="secondary">Rented: {review.itemDescription}</Text>
        <Paragraph style={{ marginBottom: 0 }}>{review.reviewText}</Paragraph>
        {review.images.length > 0 && (
          <Image.PreviewGroup>
            <Space wrap>
              {review.images.map(image => (
                <Image
                  key={image.id}
                  src={image.thumbnailUrl}
                  preview={{ src: image.url }}
                  alt={`Photo from ${review.reviewerName}`}
                  width={88}
                  height={88}
                  style={{ objectFit: 'cover', borderRadius: 8 }}
                />
              ))}
            </Space>
          </Image.PreviewGroup>
        )}
      </Space>
    </Card>
  )
}

export default function ReviewsPage() {
  const [sort, setSort] = useState<ReviewSort>('NEWEST')
  const [page, setPage] = useState(0)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['reviews', sort, page],
    queryFn: () => reviewsApi.listPublic({ sort, page }),
    placeholderData: keepPreviousData,
  })

  function changeSort(next: ReviewSort) {
    setSort(next)
    setPage(0)
  }

  return (
    <div>
      <PageHeader label="Reviews" title="Customer" accent="Reviews" />

      <Space style={{ marginBottom: 16 }}>
        <Text type="secondary">Sort by</Text>
        <Select<ReviewSort>
          value={sort}
          onChange={changeSort}
          options={SORT_OPTIONS}
          aria-label="Sort reviews"
          style={{ width: 180 }}
        />
      </Space>

      {isLoading && <LoadingSpinner />}

      {isError && <ErrorMessage message="Could not load reviews. Please try again later." />}

      {data && data.totalElements === 0 && (
        <Empty description="No reviews yet — be the first to leave one." />
      )}

      {data && data.content.length > 0 && (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          {data.content.map(review => <ReviewCard key={review.id} review={review} />)}
        </Space>
      )}

      {data && data.totalPages > 1 && (
        <Pagination
          style={{ marginTop: 24, textAlign: 'center' }}
          current={page + 1}
          total={data.totalElements}
          pageSize={PAGE_SIZE}
          showSizeChanger={false}
          onChange={next => setPage(next - 1)}
        />
      )}
    </div>
  )
}

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { Button, Empty, Image, Popconfirm, Rate, Segmented, Space, Table, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { reviewsAdminApi } from '../../api/reviews'
import PageHeader from '../../components/common/PageHeader'
import { ErrorMessage } from '../../components/common/ErrorMessage'
import { formatDatetime } from '../../utils/datetime'
import type { AdminReview, ReviewStatus } from '../../types/review'

const { Paragraph } = Typography

// Must match the backend's default page size (ReviewAdminController#listReviews).
const PAGE_SIZE = 20

type StatusFilter = ReviewStatus | 'ALL'

const FILTER_OPTIONS: { label: string; value: StatusFilter }[] = [
  { label: 'Pending', value: 'PENDING' },
  { label: 'Approved', value: 'APPROVED' },
  { label: 'Rejected', value: 'REJECTED' },
  { label: 'All', value: 'ALL' },
]

const STATUS_COLORS: Record<ReviewStatus, string> = {
  PENDING: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
}

export default function ReviewModerationPage() {
  const [filter, setFilter] = useState<StatusFilter>('PENDING')
  const [page, setPage] = useState(0)
  const queryClient = useQueryClient()

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin-reviews', filter, page],
    queryFn: () => reviewsAdminApi.listForModeration({
      status: filter === 'ALL' ? undefined : filter,
      page,
      size: PAGE_SIZE,
    }),
    placeholderData: keepPreviousData,
  })

  const totalPages = data?.totalPages ?? 0

  // BLOCKER fix: AntD's Pagination only clamps which page NUMBER it displays, it never
  // calls onChange — so moderating the only row on a non-first page would otherwise leave
  // `page` stale and the query stuck requesting a now-empty page forever. Adjusted here
  // during render (React's sanctioned way to reset state when a derived value changes,
  // https://react.dev/learn/you-might-not-need-an-effect) rather than in a useEffect, so
  // the clamp lands in the same commit instead of causing an extra render pass.
  const [lastSeenTotalPages, setLastSeenTotalPages] = useState(totalPages)
  if (totalPages > 0 && totalPages !== lastSeenTotalPages) {
    setLastSeenTotalPages(totalPages)
    if (page >= totalPages) {
      setPage(totalPages - 1)
    }
  }

  function changeFilter(next: StatusFilter) {
    setFilter(next)
    setPage(0)
  }

  // Also invalidates the public reviews cache so an owner who approves a review and
  // switches to the public /reviews page in the same session doesn't see a stale list.
  function invalidate() {
    return Promise.all([
      queryClient.invalidateQueries({ queryKey: ['admin-reviews'] }),
      queryClient.invalidateQueries({ queryKey: ['reviews'] }),
    ])
  }

  const moderate = useMutation({
    mutationFn: ({ id, status }: { id: string; status: ReviewStatus }) =>
      reviewsAdminApi.updateStatus(id, status),
    onSuccess: (_result, variables) => {
      message.success(variables.status === 'APPROVED' ? 'Review approved' : 'Review rejected')
      return invalidate()
    },
    onError: () => message.error('Failed to update review'),
  })

  const remove = useMutation({
    mutationFn: (id: string) => reviewsAdminApi.remove(id),
    onSuccess: () => {
      message.success('Review deleted')
      return invalidate()
    },
    onError: () => message.error('Failed to delete review'),
  })

  // Hardened per row: a row's Approve/Reject/Delete buttons are all disabled together
  // while ANY mutation touching that row is in flight — not just the latest `moderate`
  // call, and not just `moderate` (this also covers `remove`), so two different rows
  // can't be moderated in a way that races, and Approve/Reject/Delete on the SAME row
  // can't double-submit.
  function isRowBusy(id: string): boolean {
    return (moderate.isPending && moderate.variables?.id === id)
      || (remove.isPending && remove.variables === id)
  }

  const columns: ColumnsType<AdminReview> = [
    { title: 'Name', dataIndex: 'reviewerName' },
    { title: 'Phone', dataIndex: 'phone' },
    { title: 'Item', dataIndex: 'itemDescription' },
    {
      title: 'Rating',
      dataIndex: 'rating',
      render: (rating: number) => <Rate disabled value={rating} />,
    },
    {
      title: 'Review',
      dataIndex: 'reviewText',
      render: (text: string) => (
        <Paragraph style={{ maxWidth: 320, marginBottom: 0 }}>{text}</Paragraph>
      ),
    },
    {
      title: 'Submitted',
      dataIndex: 'createdAt',
      render: (createdAt: string) => formatDatetime(createdAt),
    },
    {
      title: 'Photos',
      dataIndex: 'images',
      render: (images: AdminReview['images']) =>
        images.length === 0 ? null : (
          <Image.PreviewGroup>
            <Space wrap>
              {images.map((image, index) => (
                <Image
                  key={image.id}
                  src={image.thumbnailUrl}
                  preview={{ src: image.url }}
                  alt={`Review photo ${index + 1}`}
                  width={48}
                  height={48}
                  style={{ objectFit: 'cover', borderRadius: 4 }}
                />
              ))}
            </Space>
          </Image.PreviewGroup>
        ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (status: ReviewStatus) => <Tag color={STATUS_COLORS[status]}>{status}</Tag>,
    },
    {
      title: 'Actions',
      render: (_, review) => {
        const busy = isRowBusy(review.id)
        return (
          <Space>
            <Button
              type="primary"
              size="small"
              disabled={busy || review.status === 'APPROVED' || review.status === 'REJECTED'}
              loading={busy && moderate.variables?.status === 'APPROVED'}
              onClick={() => moderate.mutate({ id: review.id, status: 'APPROVED' })}
            >
              Approve
            </Button>
            <Popconfirm
              title="Reject this review?"
              description="Its photos are deleted permanently."
              okText="Yes, reject"
              okButtonProps={{ danger: true }}
              onConfirm={() => moderate.mutate({ id: review.id, status: 'REJECTED' })}
            >
              <Button size="small" danger disabled={busy || review.status === 'REJECTED'}>
                Reject
              </Button>
            </Popconfirm>
            <Popconfirm
              title="Delete this review?"
              description="The review and its photos are removed permanently."
              okText="Yes, delete"
              okButtonProps={{ danger: true }}
              onConfirm={() => remove.mutate(review.id)}
            >
              <Button size="small" danger disabled={busy}>
                Delete
              </Button>
            </Popconfirm>
          </Space>
        )
      },
    },
  ]

  if (isError) {
    return <ErrorMessage message="Could not load reviews. Please try again later." />
  }

  return (
    <div>
      <PageHeader label="Reviews" title="Manage" accent="Reviews" />

      <Segmented
        options={FILTER_OPTIONS}
        value={filter}
        onChange={value => changeFilter(value as StatusFilter)}
        style={{ marginBottom: 16 }}
      />

      <Table<AdminReview>
        rowKey="id"
        loading={isLoading}
        columns={columns}
        dataSource={data?.content ?? []}
        scroll={{ x: 'max-content' }}
        pagination={{
          current: page + 1,
          total: data?.totalElements ?? 0,
          pageSize: PAGE_SIZE,
          showSizeChanger: false,
          onChange: next => setPage(next - 1),
        }}
        locale={{ emptyText: <Empty description="No reviews in this state" /> }}
      />
    </div>
  )
}

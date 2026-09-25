import { describe, it, expect } from 'vitest'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { renderWithProviders, screen, waitFor, within } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import ReviewModerationPage from './ReviewModerationPage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })
const page = (content: unknown[], overrides: Partial<{ totalElements: number; totalPages: number; number: number }> = {}) => ({
  content, totalElements: content.length, totalPages: 1, number: 0, size: 20, ...overrides,
})

describe('ReviewModerationPage', () => {
  it('defaults the status filter to Pending', async () => {
    const seenStatuses: (string | null)[] = []
    server.use(http.get('*/api/reviews', ({ request }) => {
      seenStatuses.push(new URL(request.url).searchParams.get('status'))
      return ok(page([f.anAdminReview()]))
    }))

    const { getByRole } = renderWithProviders(<ReviewModerationPage />, { route: '/reviews/manage' })

    await waitFor(() => expect(seenStatuses).toContain('PENDING'))
    expect(getByRole('radio', { name: 'Pending' })).toBeChecked()
  })

  it('shows the reviewer phone number to the owner', async () => {
    server.use(http.get('*/api/reviews', () => ok(page([f.anAdminReview({ phone: '9876543210' })]))))

    const { findByText } = renderWithProviders(<ReviewModerationPage />, { route: '/reviews/manage' })

    expect(await findByText('9876543210')).toBeInTheDocument()
  })

  it('approving a review sends APPROVED and refetches the queue', async () => {
    const user = userEvent.setup()
    let getCount = 0
    let patchedBody: { status?: string } | null = null
    server.use(
      http.get('*/api/reviews', () => {
        getCount += 1
        return ok(page([f.anAdminReview()]))
      }),
      http.patch('*/api/reviews/:id/status', async ({ request }) => {
        patchedBody = (await request.json()) as { status?: string }
        return ok(f.anAdminReview({ status: 'APPROVED' }))
      }),
    )

    const { findByRole } = renderWithProviders(<ReviewModerationPage />, { route: '/reviews/manage' })

    const countBeforeApprove = getCount
    await user.click(await findByRole('button', { name: /approve/i }))

    await waitFor(() => expect(patchedBody).toEqual({ status: 'APPROVED' }))
    await waitFor(() => expect(getCount).toBeGreaterThan(countBeforeApprove))
  })

  it('deletes a review only after the Popconfirm is confirmed', async () => {
    const user = userEvent.setup()
    let deleted = false
    server.use(
      http.get('*/api/reviews', () => ok(page([f.anAdminReview()]))),
      http.delete('*/api/reviews/:id', () => { deleted = true; return ok(null) }),
    )

    const { findByRole, getByRole } = renderWithProviders(<ReviewModerationPage />, { route: '/reviews/manage' })

    await user.click(await findByRole('button', { name: /delete/i }))
    expect(deleted).toBe(false) // nothing happens until the popconfirm is accepted

    await user.click(getByRole('button', { name: 'Yes, delete' }))
    await waitFor(() => expect(deleted).toBe(true))
  })

  it('rejects a review only after the Popconfirm is confirmed', async () => {
    const user = userEvent.setup()
    let patchedBody: { status?: string } | null = null
    server.use(
      http.get('*/api/reviews', () => ok(page([f.anAdminReview()]))),
      http.patch('*/api/reviews/:id/status', async ({ request }) => {
        patchedBody = (await request.json()) as { status?: string }
        return ok(f.anAdminReview({ status: 'REJECTED' }))
      }),
    )

    const { findByRole, getByRole } = renderWithProviders(<ReviewModerationPage />, { route: '/reviews/manage' })

    await user.click(await findByRole('button', { name: /reject/i }))
    expect(patchedBody).toBeNull() // nothing happens until the popconfirm is accepted — rejecting deletes photos permanently

    await user.click(getByRole('button', { name: 'Yes, reject' }))
    await waitFor(() => expect(patchedBody).toEqual({ status: 'REJECTED' }))
  })

  it('disables Approve on rows that are already approved or rejected', async () => {
    server.use(http.get('*/api/reviews', () => ok(page([
      f.anAdminReview({ id: 'review-pending', status: 'PENDING' }),
      f.anAdminReview({ id: 'review-approved', status: 'APPROVED' }),
      f.anAdminReview({ id: 'review-rejected', status: 'REJECTED' }),
    ], { totalElements: 3 }))))

    renderWithProviders(<ReviewModerationPage />, { route: '/reviews/manage' })

    // The header row is present from the first render, before the data loads — wait for
    // all three body rows so we don't grab the empty-state placeholder row instead.
    const rows = await waitFor(() => {
      const found = screen.getAllByRole('row')
      expect(found).toHaveLength(4)
      return found
    })
    const [, pendingRow, approvedRow, rejectedRow] = rows // rows[0] is the header row

    // The backend does not reject re-approving an already-decided review (that guard lives
    // only here, in the UI) — this test is the only thing that would catch someone
    // accidentally dropping the `status === 'APPROVED' || status === 'REJECTED'` check.
    expect(within(pendingRow).getByRole('button', { name: /approve/i })).toBeEnabled()
    expect(within(approvedRow).getByRole('button', { name: /approve/i })).toBeDisabled()
    expect(within(rejectedRow).getByRole('button', { name: /approve/i })).toBeDisabled()
  })

  it('disables a row\'s other actions while a mutation on that row is in flight', async () => {
    const user = userEvent.setup()
    server.use(
      http.get('*/api/reviews', () => ok(page([f.anAdminReview({ id: 'review-1' })]))),
      http.patch('*/api/reviews/:id/status', async () => {
        await new Promise(resolve => setTimeout(resolve, 50))
        return ok(f.anAdminReview({ id: 'review-1', status: 'APPROVED' }))
      }),
    )

    const { findByRole } = renderWithProviders(<ReviewModerationPage />, { route: '/reviews/manage' })

    await user.click(await findByRole('button', { name: /approve/i }))

    // While the Approve request for this row is still in flight, Reject and Delete on the
    // SAME row must not be clickable — otherwise two mutations could race on one review.
    // Both are checked in a single waitFor so there's no gap between them for the
    // artificial 50ms delay above to close.
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /reject/i })).toBeDisabled()
      expect(screen.getByRole('button', { name: /delete/i })).toBeDisabled()
    })

    await waitFor(() => expect(screen.getByRole('button', { name: /reject/i })).toBeEnabled())
  })

  it('clamps back a page when moderating empties the current (last) page', async () => {
    const user = userEvent.setup()
    let currentPage = 0
    let approved = false

    // The table's PAGE_SIZE is 20, so totalElements must exceed 20 for AntD's Pagination
    // to render a page-2 link. Page 0 has one review, page 1 has the sole review being
    // moderated. Approving it removes it from the (still-PENDING-filtered) queue, so the
    // backend's next response for page 1 has no content and totalElements drops to 20
    // (exactly one page) — the page state must clamp back to 0 instead of showing an
    // empty page while page 0 still has data.
    server.use(
      http.get('*/api/reviews', ({ request }) => {
        const requestedPage = Number(new URL(request.url).searchParams.get('page') ?? '0')
        currentPage = requestedPage

        if (!approved) {
          return requestedPage === 0
            ? ok(page(
                [f.anAdminReview({ id: 'review-page0', itemDescription: 'Item on page one' })],
                { totalElements: 21, totalPages: 2, number: 0 },
              ))
            : ok(page(
                [f.anAdminReview({ id: 'review-page1', itemDescription: 'Item on page two' })],
                { totalElements: 21, totalPages: 2, number: 1 },
              ))
        }

        return requestedPage === 0
          ? ok(page(
              [f.anAdminReview({ id: 'review-page0', itemDescription: 'Item on page one' })],
              { totalElements: 20, totalPages: 1, number: 0 },
            ))
          : ok(page([], { totalElements: 20, totalPages: 1, number: requestedPage }))
      }),
      http.patch('*/api/reviews/:id/status', () => {
        approved = true
        return ok(f.anAdminReview({ id: 'review-page1', status: 'APPROVED' }))
      }),
    )

    const { findByRole, findByText } = renderWithProviders(<ReviewModerationPage />, { route: '/reviews/manage' })

    await findByText('Item on page one')
    await user.click(getPaginationItem(document, '2'))
    await findByText('Item on page two')
    expect(currentPage).toBe(1)

    await user.click(await findByRole('button', { name: /approve/i }))

    // After the approve refetch, page 1 (index 1) is now empty — the page must clamp
    // back to page 0 rather than keep requesting the now-empty page.
    await waitFor(() => expect(currentPage).toBe(0))
    await findByText('Item on page one')
  })
})

function getPaginationItem(doc: Document, label: string): HTMLElement {
  const item = doc.querySelector(`.ant-pagination-item-${label} a`) as HTMLElement | null
  if (!item) throw new Error(`Pagination item "${label}" not found`)
  return item
}

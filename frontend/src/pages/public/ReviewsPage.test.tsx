import { describe, it, expect } from 'vitest'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { renderWithProviders, waitFor } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import ReviewsPage from './ReviewsPage'

// Default msw handler (src/test/handlers.ts) seeds:
//   review-1 — Priya S, 5★, Red bridal lehenga, one photo, created 2026-09-20
//   review-2 — Anil K,  4★, Maroon sherwani, no photos,   created 2026-09-18

const pageOf = (content: unknown[], totalElements: number, totalPages: number) =>
  HttpResponse.json({
    success: true,
    data: { content, totalElements, totalPages, number: 0, size: 10 },
    error: null,
  })

describe('ReviewsPage', () => {
  it('renders each review with name, item, text, date, and photo thumbnail', async () => {
    const { findByText, getByText, getByAltText } =
      renderWithProviders(<ReviewsPage />, { route: '/reviews' })

    expect(await findByText('Priya S')).toBeInTheDocument()
    expect(getByText('Anil K')).toBeInTheDocument()
    expect(getByText('Rented: Red bridal lehenga')).toBeInTheDocument()
    expect(getByText('Beautiful outfit, fit perfectly.')).toBeInTheDocument()
    expect(getByText('20 Sep 2026', { exact: false })).toBeInTheDocument()
    expect(getByText('18 Sep 2026', { exact: false })).toBeInTheDocument()
    expect(getByAltText('Photo from Priya S'))
      .toHaveAttribute('src', 'https://cdn.example.com/review-1-thumb.jpg')
  })

  it('never renders a phone number', async () => {
    // Simulates a backend regression that starts leaking phone: the page must still not echo it.
    server.use(
      http.get('*/api/public/reviews', () =>
        pageOf([{ ...f.aPublicReview(), phone: '9876543210' }], 1, 1)),
    )
    const { findByText } = renderWithProviders(<ReviewsPage />, { route: '/reviews' })

    await findByText('Priya S')

    expect(document.body.innerHTML).not.toMatch(/\d{10}/)
  })

  it('requests page=1 from the server when the user clicks page 2', async () => {
    const requestedPages: string[] = []
    server.use(
      http.get('*/api/public/reviews', ({ request }) => {
        requestedPages.push(new URL(request.url).searchParams.get('page') ?? 'MISSING')
        return pageOf([f.aPublicReview()], 25, 3)
      }),
    )
    const user = userEvent.setup()
    const { findByText, getByTitle } = renderWithProviders(<ReviewsPage />, { route: '/reviews' })

    await findByText('Priya S')
    await user.click(getByTitle('2'))

    await waitFor(() => expect(requestedPages).toEqual(['0', '1']))
  })

  it('requests HIGHEST_RATED from page 0 when the sort changes on a later page', async () => {
    const requests: { sort: string | null; page: string | null }[] = []
    server.use(
      http.get('*/api/public/reviews', ({ request }) => {
        const params = new URL(request.url).searchParams
        requests.push({ sort: params.get('sort'), page: params.get('page') })
        return pageOf([f.aPublicReview()], 25, 3)
      }),
    )
    const user = userEvent.setup()
    const { findByText, getByTitle, getByRole, findByTitle } =
      renderWithProviders(<ReviewsPage />, { route: '/reviews' })

    await findByText('Priya S')
    await user.click(getByTitle('3'))
    await waitFor(() => expect(requests).toContainEqual({ sort: 'NEWEST', page: '2' }))

    await user.click(getByRole('combobox', { name: 'Sort reviews' }))
    await user.click(await findByTitle('Highest rated'))

    await waitFor(() => expect(requests[requests.length - 1]).toEqual({ sort: 'HIGHEST_RATED', page: '0' }))
  })

  it('shows an empty state when no reviews are approved yet', async () => {
    server.use(http.get('*/api/public/reviews', () => pageOf([], 0, 0)))

    const { findByText } = renderWithProviders(<ReviewsPage />, { route: '/reviews' })

    expect(await findByText(/no reviews yet/i)).toBeInTheDocument()
  })
})

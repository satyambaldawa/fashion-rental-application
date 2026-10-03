import { describe, it, expect } from 'vitest'
import userEvent from '@testing-library/user-event'
import { useLocation } from 'react-router-dom'
import { http, HttpResponse } from 'msw'
import { renderWithProviders, waitFor, flush } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import GalleryPage from './GalleryPage'

// Seeded by the default msw handler (src/test/handlers.ts):
//   gallery-1 — COSTUME, caption "Royal Sherwani"
//   gallery-2 — PAGDI,   caption null

function LocationProbe() {
  const location = useLocation()
  return (
    <output aria-label="current location" data-location-key={location.key}>
      {location.pathname + location.search}
    </output>
  )
}

function recordGalleryRequests() {
  const seen: (string | null)[] = []
  server.use(
    http.get('*/api/public/gallery', ({ request }) => {
      const category = new URL(request.url).searchParams.get('category')
      seen.push(category)
      const images = [
        f.aGalleryImage({ id: 'gallery-1', category: 'COSTUME', caption: 'Royal Sherwani' }),
        f.aGalleryImage({ id: 'gallery-2', category: 'PAGDI', caption: null }),
      ]
      return HttpResponse.json({
        success: true,
        data: category ? images.filter(image => image.category === category) : images,
        error: null,
      })
    }),
  )
  return seen
}

describe('GalleryPage', () => {
  it('groups images into one section per category with a heading for each, in the default All view', async () => {
    const { findByRole } = renderWithProviders(<GalleryPage />, { route: '/gallery' })

    expect(await findByRole('heading', { name: 'Costume' })).toBeInTheDocument()
    expect(await findByRole('heading', { name: 'Pagdi' })).toBeInTheDocument()
  })

  it('shows a caption only for the image that has one', async () => {
    const { findByRole, getByRole } = renderWithProviders(<GalleryPage />, { route: '/gallery' })

    // gallery-1 (COSTUME) has a caption — its section renders the secondary caption text.
    const costumeSection = (await findByRole('heading', { name: 'Costume' })).closest('div')!
    expect(costumeSection.querySelector('.ant-typography-secondary')).toHaveTextContent('Royal Sherwani')

    // gallery-2 (PAGDI) has caption: null — its section must render no caption element at all,
    // not an empty one. A regression that drops the `image.caption &&` guard would still emit
    // the secondary <span>, just empty — this catches that, not just "text is absent".
    const pagdiSection = getByRole('heading', { name: 'Pagdi' }).closest('div')!
    expect(pagdiSection.querySelector('.ant-typography-secondary')).not.toBeInTheDocument()
  })

  it('clicking a category chip re-queries for just that category and hides the now-redundant section title', async () => {
    const user = userEvent.setup()
    const { getByRole, findByText, queryByText, queryByRole, container } =
      renderWithProviders(<GalleryPage />, { route: '/gallery' })

    expect(await findByText('Royal Sherwani')).toBeInTheDocument()
    expect(container.querySelectorAll('img')).toHaveLength(2)

    await user.click(getByRole('button', { name: 'Pagdi' }))

    // Re-query happened over the network (msw filters by ?category=PAGDI): the COSTUME
    // image is gone entirely, not just hidden client-side.
    await waitFor(() => expect(container.querySelectorAll('img')).toHaveLength(1))
    expect(queryByText('Royal Sherwani')).not.toBeInTheDocument()

    // Exactly one category is active, so the section heading naming it would be redundant
    // with the active chip — it must not render.
    expect(queryByRole('heading', { name: 'Pagdi' })).not.toBeInTheDocument()
  })

  it('shows the AntD empty state when the gallery has no images', async () => {
    server.use(
      http.get('*/api/public/gallery', () => HttpResponse.json({ success: true, data: [], error: null })),
    )

    const { findByText, queryByRole } = renderWithProviders(<GalleryPage />, { route: '/gallery' })

    expect(await findByText('No images to show')).toBeInTheDocument()
    expect(queryByRole('heading', { name: 'Costume' })).not.toBeInTheDocument()
  })

  it('clicking a category chip updates the URL to include the category', async () => {
    const user = userEvent.setup()
    const { getByRole, findByText } = renderWithProviders(
      <><GalleryPage /><LocationProbe /></>,
      { route: '/gallery' },
    )

    expect(await findByText('Royal Sherwani')).toBeInTheDocument()

    await user.click(getByRole('button', { name: 'Pagdi' }))

    expect(getByRole('status', { name: 'current location' })).toHaveTextContent('/gallery?category=PAGDI')
    expect(getByRole('button', { name: 'Pagdi' })).toHaveAttribute('aria-pressed', 'true')
    expect(getByRole('button', { name: 'All' })).toHaveAttribute('aria-pressed', 'false')

    await user.click(getByRole('button', { name: 'Navratri Collection' }))

    expect(getByRole('status', { name: 'current location' })).toHaveTextContent('/gallery?category=NAVRATRI_COLLECTION')
  })

  it('pre-selects the category from a deep link and matches the manual-click result', async () => {
    const seen = recordGalleryRequests()
    const { getByRole, queryByText, queryByRole, container } =
      renderWithProviders(<GalleryPage />, { route: '/gallery?category=PAGDI' })

    await waitFor(() => expect(container.querySelectorAll('img')).toHaveLength(1))
    expect(queryByText('Royal Sherwani')).not.toBeInTheDocument()
    expect(queryByRole('heading', { name: 'Pagdi' })).not.toBeInTheDocument()

    expect(getByRole('button', { name: 'Pagdi' })).toHaveAttribute('aria-pressed', 'true')
    expect(seen).toEqual(['PAGDI'])
  })

  it('selecting All clears the category param and restores the full grid', async () => {
    const user = userEvent.setup()
    const { getByRole, findByText } = renderWithProviders(
      <><GalleryPage /><LocationProbe /></>,
      { route: '/gallery?category=PAGDI' },
    )

    await user.click(getByRole('button', { name: 'All' }))

    expect(getByRole('status', { name: 'current location' })).toHaveTextContent('/gallery')
    expect(getByRole('status', { name: 'current location' })).not.toHaveTextContent('?')
    expect(await findByText('Royal Sherwani')).toBeInTheDocument()
    expect(getByRole('heading', { name: 'Pagdi' })).toBeInTheDocument()
    expect(getByRole('button', { name: 'All' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('preserves unrelated query params when clearing the category', async () => {
    const user = userEvent.setup()
    const { getByRole } = renderWithProviders(
      <><GalleryPage /><LocationProbe /></>,
      { route: '/gallery?category=PAGDI&utm_source=wa' },
    )

    await user.click(getByRole('button', { name: 'All' }))

    expect(getByRole('status', { name: 'current location' })).toHaveTextContent('/gallery?utm_source=wa')
  })

  it('shows "Category not found" for an unknown category and makes no API request', async () => {
    const seen = recordGalleryRequests()
    const { findByText, queryByRole, getByRole } =
      renderWithProviders(<GalleryPage />, { route: '/gallery?category=BOGUS' })

    expect(await findByText('Category not found')).toBeInTheDocument()
    expect(queryByRole('img')).not.toBeInTheDocument()
    expect(queryByRole('heading')).not.toBeInTheDocument()

    for (const opt of ['All', 'Costume', 'Pagdi']) {
      expect(getByRole('button', { name: opt })).toHaveAttribute('aria-pressed', 'false')
    }

    await flush()
    expect(seen).toEqual([])
  })

  it('recovers from an unknown category when any chip is clicked', async () => {
    const user = userEvent.setup()
    const { getByRole, findByText, queryByText } = renderWithProviders(
      <><GalleryPage /><LocationProbe /></>,
      { route: '/gallery?category=BOGUS' },
    )

    expect(await findByText('Category not found')).toBeInTheDocument()

    await user.click(getByRole('button', { name: 'Costume' }))

    expect(getByRole('status', { name: 'current location' })).toHaveTextContent('/gallery?category=COSTUME')
    expect(queryByText('Category not found')).not.toBeInTheDocument()
    expect(await findByText('Royal Sherwani')).toBeInTheDocument()

    await user.click(getByRole('button', { name: 'All' }))

    expect(getByRole('status', { name: 'current location' })).toHaveTextContent('/gallery')
    expect(await findByText('Royal Sherwani')).toBeInTheDocument()
    expect(getByRole('heading', { name: 'Pagdi' })).toBeInTheDocument()
  })

  it('treats a category value as case-sensitive, rejecting a lowercase match', async () => {
    const { findByText } = renderWithProviders(<GalleryPage />, { route: '/gallery?category=pagdi' })

    expect(await findByText('Category not found')).toBeInTheDocument()
  })

  it('treats an empty category param as All rather than an unknown category', async () => {
    const { findByText, queryByText, getByRole } =
      renderWithProviders(<GalleryPage />, { route: '/gallery?category=' })

    expect(await findByText('Royal Sherwani')).toBeInTheDocument()
    expect(queryByText('Category not found')).not.toBeInTheDocument()
    expect(getByRole('button', { name: 'All' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('clicking the already-active chip does not trigger a navigation (no-op guard)', async () => {
    const user = userEvent.setup()
    const { getByRole, container } = renderWithProviders(
      <><GalleryPage /><LocationProbe /></>,
      { route: '/gallery?category=PAGDI' },
    )

    await waitFor(() => expect(container.querySelectorAll('img')).toHaveLength(1))
    const keyBeforeClick = getByRole('status', { name: 'current location' }).getAttribute('data-location-key')

    await user.click(getByRole('button', { name: 'Pagdi' }))

    // A real navigation (even a same-URL `replace`) mints a new history key. Confirming the key
    // is unchanged proves `selectCategory`'s no-op guard skipped `setSearchParams` entirely,
    // not just that the resulting URL text happens to look the same.
    expect(getByRole('status', { name: 'current location' })).toHaveAttribute('data-location-key', keyBeforeClick)
    expect(getByRole('status', { name: 'current location' })).toHaveTextContent('/gallery?category=PAGDI')
  })
})

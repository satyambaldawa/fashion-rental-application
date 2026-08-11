import { describe, it, expect } from 'vitest'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { renderWithProviders, waitFor } from '../../test/render'
import { server } from '../../test/server'
import GalleryPage from './GalleryPage'

// Seeded by the default msw handler (src/test/handlers.ts):
//   gallery-1 — COSTUME, caption "Royal Sherwani"
//   gallery-2 — PAGDI,   caption null

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
})

import { describe, it, expect } from 'vitest'
import { http, HttpResponse } from 'msw'
import { renderWithProviders, waitFor } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import AboutPage from './AboutPage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })

// Each image gets its own imageUrl/thumbnailUrl (rather than the factory's shared
// default) so tests can tell distinct gallery images apart by `src`.
function withUniqueUrls(overrides: Parameters<typeof f.aGalleryImage>[0] & { id: string }) {
  return f.aGalleryImage({
    imageUrl: `https://cdn.example.com/${overrides.id}.jpg`,
    thumbnailUrl: `https://cdn.example.com/${overrides.id}-thumb.jpg`,
    ...overrides,
  })
}

function mockGalleryImages() {
  server.use(
    http.get('*/api/public/gallery', () =>
      ok([
        withUniqueUrls({ id: 'g-navratri', category: 'NAVRATRI_COLLECTION', caption: 'Chaniya Choli' }),
        withUniqueUrls({ id: 'g-traditional', category: 'TRADITIONAL', caption: 'Nauvari Saree' }),
        withUniqueUrls({ id: 'g-costume', category: 'COSTUME', caption: 'Royal Sherwani' }),
        withUniqueUrls({ id: 'g-dress', category: 'DRESS', caption: 'Lehenga' }),
        withUniqueUrls({ id: 'g-pagdi', category: 'PAGDI', caption: 'Rajputi Pagdi' }),
        withUniqueUrls({ id: 'g-ornaments', category: 'ORNAMENTS', caption: 'Kundan Set' }),
      ]),
    ),
  )
}

describe('AboutPage', () => {
  it('shows the shop-front photo in the story section', () => {
    const { getByAltText } = renderWithProviders(<AboutPage />, { route: '/about' })

    expect(getByAltText("Manisha's Fancy Collection storefront in Ravet")).toHaveAttribute(
      'src',
      '/about/shop-front.jpg',
    )
  })

  it('links "Get Directions" to the shop\'s Google Maps listing', () => {
    const { getByRole } = renderWithProviders(<AboutPage />, { route: '/about' })

    expect(getByRole('link', { name: /Get Directions/i })).toHaveAttribute(
      'href',
      'https://maps.app.goo.gl/xR1Rjn4QxWJ3PKeQ8',
    )
  })

  it('embeds a Google Maps preview of the shop location', () => {
    const { getByTitle } = renderWithProviders(<AboutPage />, { route: '/about' })

    expect(getByTitle("Manisha's Drapery location")).toHaveAttribute(
      'src',
      'https://www.google.com/maps?q=18.6486092,73.7511599&z=16&output=embed',
    )
  })

  it('lists hours for all seven days of the week', () => {
    const { getByText, getAllByText } = renderWithProviders(<AboutPage />, { route: '/about' })

    expect(getByText('Monday')).toBeInTheDocument()
    expect(getByText('Sunday')).toBeInTheDocument()
    // Sunday is the only day with the shorter, evening-only hours.
    expect(getByText('5:00 PM – 9:00 PM')).toBeInTheDocument()
    // The other six days (Mon–Sat) share the same two-shift hours string.
    expect(getAllByText('10:00 AM – 1:00 PM, 5:00 PM – 9:00 PM')).toHaveLength(6)
  })

  it('links "Chat on WhatsApp" to wa.me with a pre-filled message, no raw spaces', () => {
    const { getByRole } = renderWithProviders(<AboutPage />, { route: '/about' })

    const href = getByRole('link', { name: /Chat on WhatsApp/i }).getAttribute('href')
    expect(href).toMatch(/^https:\/\/wa\.me\/917720001926\?text=/)
    expect(href).not.toMatch(/ /)
  })

  it('links "Follow on Instagram" to the shop\'s Instagram profile', () => {
    const { getByRole } = renderWithProviders(<AboutPage />, { route: '/about' })

    expect(getByRole('link', { name: /Follow on Instagram/i })).toHaveAttribute(
      'href',
      'https://www.instagram.com/manishasdrapery/',
    )
  })

  it('shows a tel: link with the shop phone number', () => {
    const { getByRole } = renderWithProviders(<AboutPage />, { route: '/about' })

    expect(getByRole('link', { name: '+91 77200 01926' })).toHaveAttribute(
      'href',
      'tel:+917720001926',
    )
  })

  it('links to the public Gallery and Reviews pages', () => {
    const { getByRole } = renderWithProviders(<AboutPage />, { route: '/about' })

    expect(getByRole('link', { name: /View Full Gallery/i })).toHaveAttribute('href', '/gallery')
    expect(getByRole('link', { name: /Read All Reviews/i })).toHaveAttribute('href', '/reviews')
  })

  it('shows "What Customers Say" as its own section with a preview of real reviews', async () => {
    // Default msw handler seeds two public reviews (Priya S / 5★, Anil K / 4★).
    const { findByText, getByText } = renderWithProviders(<AboutPage />, { route: '/about' })

    expect(await findByText('Priya S')).toBeInTheDocument()
    expect(getByText('Anil K')).toBeInTheDocument()
    expect(getByText(/Beautiful outfit, fit perfectly\./)).toBeInTheDocument()
  })

  it('shows the owner-curated static image for each "What We Offer" bucket', () => {
    // These are hand-picked assets in public/about/, independent of gallery contents —
    // no msw mock needed, and no gallery data should ever change what shows here.
    const { getByAltText } = renderWithProviders(<AboutPage />, { route: '/about' })

    expect(getByAltText('Navratri & Festive Wear')).toHaveAttribute(
      'src',
      '/about/offer-navratri-festive.jpg',
    )
    expect(getByAltText('Costumes for Every Occasion')).toHaveAttribute(
      'src',
      '/about/offer-traditional.jpg',
    )
    expect(getByAltText('Dress-Up Essentials')).toHaveAttribute('src', '/about/offer-dressup.jpg')
    expect(getByAltText("Kids' Drapery")).toHaveAttribute('src', '/about/offer-kids-drapery.jpg')
  })

  it('renders every fetched gallery image in the collection carousel', async () => {
    // react-slick marks every slide but the active one aria-hidden, which hides them
    // from role-based queries — use alt-text queries (not filtered by aria-hidden)
    // to see every image it actually mounted, cloned slides included.
    mockGalleryImages()
    const { findAllByAltText } = renderWithProviders(<AboutPage />, { route: '/about' })

    await waitFor(async () => {
      const slides = await findAllByAltText(
        /Chaniya Choli|Nauvari Saree|Royal Sherwani|Lehenga|Rajputi Pagdi|Kundan Set/,
      )
      const uniqueSrcs = new Set(slides.map(img => (img as HTMLImageElement).src))
      expect(uniqueSrcs.size).toBe(6)
    })
  })

  it('caps the carousel at CAROUSEL_SIZE images when more are available', async () => {
    server.use(
      http.get('*/api/public/gallery', () =>
        ok(
          Array.from({ length: 9 }, (_, i) =>
            withUniqueUrls({ id: `g-${i}`, category: 'COSTUME', caption: `Costume ${i}` }),
          ),
        ),
      ),
    )
    const { findAllByAltText } = renderWithProviders(<AboutPage />, { route: '/about' })

    await waitFor(async () => {
      const slides = await findAllByAltText(/^Costume \d$/)
      const uniqueSrcs = new Set(slides.map(img => (img as HTMLImageElement).src))
      expect(uniqueSrcs.size).toBe(8)
    })
  })
})

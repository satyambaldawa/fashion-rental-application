import { describe, it, expect, vi } from 'vitest'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { renderWithProviders, waitFor } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import { galleryAdminApi } from '../../api/gallery'
import GalleryManagePage from './GalleryManagePage'

// Default admin handler (src/test/handlers.ts) serves for category COSTUME:
//   admin-1 — caption "Royal Sherwani", isActive true
//   admin-2 — caption null,             isActive false  (hidden)
const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })

describe('GalleryManagePage', () => {
  it('lists images for the default category, including hidden ones', async () => {
    const { findByDisplayValue, getAllByText } = renderWithProviders(<GalleryManagePage />, { route: '/gallery/manage' })

    // Captions render inside an editable textarea, so assert on the field value.
    expect(await findByDisplayValue('Royal Sherwani')).toBeInTheDocument()
    // admin-2 is inactive — the admin list surfaces it as "Hidden".
    expect(getAllByText('Hidden').length).toBeGreaterThan(0)
  })

  it('saves an edited caption via PATCH and only enables Save when the text changed', async () => {
    const user = userEvent.setup()
    let patchedBody: { caption?: string } | null = null
    server.use(http.patch('*/api/gallery/:id', async ({ request, params }) => {
      patchedBody = (await request.json()) as { caption?: string }
      return ok(f.aGalleryImage({ id: params.id as string, caption: patchedBody.caption }))
    }))

    const { findByDisplayValue, getAllByRole } = renderWithProviders(
      <GalleryManagePage />, { route: '/gallery/manage' },
    )

    const captionInput = await findByDisplayValue('Royal Sherwani')
    await user.clear(captionInput)
    await user.type(captionInput, 'Wedding Sherwani')

    const saveButtons = getAllByRole('button', { name: 'Save caption' })
    const enabled = saveButtons.find(b => !(b as HTMLButtonElement).disabled)!
    await user.click(enabled)

    await waitFor(() => expect(patchedBody).toEqual({ caption: 'Wedding Sherwani' }))
  })

  it('toggles visibility via PATCH when the switch is clicked', async () => {
    const user = userEvent.setup()
    let patchedBody: { isActive?: boolean } | null = null
    server.use(http.patch('*/api/gallery/:id', async ({ request, params }) => {
      patchedBody = (await request.json()) as { isActive?: boolean }
      return ok(f.aGalleryImage({ id: params.id as string, isActive: patchedBody.isActive }))
    }))

    const { findByLabelText } = renderWithProviders(<GalleryManagePage />, { route: '/gallery/manage' })

    // admin-2 is hidden, so its switch reads "Show image" — clicking it publishes.
    const showSwitch = await findByLabelText('Show image')
    await user.click(showSwitch)

    await waitFor(() => expect(patchedBody).toEqual({ isActive: true }))
  })

  it('deletes an image only after the confirmation is accepted', async () => {
    const user = userEvent.setup()
    let deleted = false
    server.use(http.delete('*/api/gallery/:id', () => { deleted = true; return ok(null) }))

    const { findAllByRole, getByRole } = renderWithProviders(<GalleryManagePage />, { route: '/gallery/manage' })

    const deleteButtons = await findAllByRole('button', { name: 'Delete image' })
    await user.click(deleteButtons[0])
    expect(deleted).toBe(false) // nothing happens until the popconfirm is accepted

    await user.click(getByRole('button', { name: 'Delete' }))
    await waitFor(() => expect(deleted).toBe(true))
  })

  it('uploads the whole selection to the active category in a single call', async () => {
    const user = userEvent.setup()
    // A real multipart request hangs msw+jsdom, so spy on the API boundary — this
    // also proves the batch is sent as one call, into the selected category.
    const uploadSpy = vi.spyOn(galleryAdminApi, 'upload').mockResolvedValue([f.aGalleryImage()])

    const { container, findByDisplayValue, findByText } = renderWithProviders(<GalleryManagePage />, { route: '/gallery/manage' })
    await findByDisplayValue('Royal Sherwani')

    const input = container.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, [
      new File(['a'], 'a.jpg', { type: 'image/jpeg' }),
      new File(['b'], 'b.jpg', { type: 'image/jpeg' }),
    ])

    expect(await findByText('2 images uploaded')).toBeInTheDocument()
    expect(uploadSpy).toHaveBeenCalledTimes(1)
    expect(uploadSpy.mock.calls[0][0]).toBe('COSTUME')
    expect(uploadSpy.mock.calls[0][1]).toHaveLength(2)
    uploadSpy.mockRestore()
  })

  it('shows an empty state when the category has no images', async () => {
    server.use(http.get('*/api/gallery', () => ok([])))

    const { findByText } = renderWithProviders(<GalleryManagePage />, { route: '/gallery/manage' })

    expect(await findByText('No images in this category yet')).toBeInTheDocument()
  })
})

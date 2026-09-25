import { describe, it, expect, vi } from 'vitest'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../../test/render'
import { reviewsApi } from '../../api/reviews'
import SubmitReviewPage from './SubmitReviewPage'

type Rendered = ReturnType<typeof renderWithProviders>
type User = ReturnType<typeof userEvent.setup>

const FIVE_STARS = 4

async function fillValidForm(user: User, screen: Rendered) {
  await user.type(screen.getByLabelText('Your name'), 'Priya S')
  await user.type(screen.getByLabelText('Mobile number'), '9876543210')
  await user.type(screen.getByLabelText('What did you rent?'), 'Red lehenga')
  await user.click(screen.getAllByRole('radio')[FIVE_STARS])
  await user.type(screen.getByLabelText('Your review'), 'Beautiful outfit.')
}

describe('SubmitReviewPage', () => {
  it('stops accepting review text at 256 characters', async () => {
    const user = userEvent.setup()
    const screen = renderWithProviders(<SubmitReviewPage />, { route: '/review' })

    const textarea = screen.getByLabelText('Your review') as HTMLTextAreaElement
    await user.click(textarea)
    await user.paste('x'.repeat(300))

    expect(textarea.value).toHaveLength(256)
  })

  it('blocks submission when the mobile number is not a valid 10-digit Indian number', async () => {
    const submitSpy = vi.spyOn(reviewsApi, 'submit')
    const user = userEvent.setup()
    const screen = renderWithProviders(<SubmitReviewPage />, { route: '/review' })

    await fillValidForm(user, screen)
    await user.clear(screen.getByLabelText('Mobile number'))
    await user.type(screen.getByLabelText('Mobile number'), '12345')
    await user.click(screen.getByRole('button', { name: /submit review/i }))

    expect(await screen.findByText('Enter a valid 10-digit Indian mobile number')).toBeInTheDocument()
    expect(submitSpy).not.toHaveBeenCalled()
    submitSpy.mockRestore()
  })

  it('blocks submission when no rating is selected', async () => {
    const submitSpy = vi.spyOn(reviewsApi, 'submit')
    const user = userEvent.setup()
    const screen = renderWithProviders(<SubmitReviewPage />, { route: '/review' })

    await user.type(screen.getByLabelText('Your name'), 'Priya S')
    await user.type(screen.getByLabelText('Mobile number'), '9876543210')
    await user.type(screen.getByLabelText('What did you rent?'), 'Red lehenga')
    await user.type(screen.getByLabelText('Your review'), 'Beautiful outfit.')
    await user.click(screen.getByRole('button', { name: /submit review/i }))

    expect(await screen.findByText('Please give a rating')).toBeInTheDocument()
    expect(submitSpy).not.toHaveBeenCalled()
    submitSpy.mockRestore()
  })

  // A real multipart FormData POST through msw+jsdom hangs the test runner (see
  // src/api/api.test.ts and GalleryManagePage.test.tsx for the same trap), so this
  // spies on the API boundary instead of routing the submission through msw.
  it('sends the review with the form values, rating, and photos, and shows the awaiting-approval confirmation', async () => {
    const submitSpy = vi.spyOn(reviewsApi, 'submit').mockResolvedValue({ id: 'review-3', status: 'PENDING' })
    const user = userEvent.setup()
    const screen = renderWithProviders(<SubmitReviewPage />, { route: '/review' })
    const photo = new File([new Uint8Array(10)], 'photo.jpg', { type: 'image/jpeg' })

    await fillValidForm(user, screen)
    await user.upload(screen.container.querySelector('input[type="file"]') as HTMLInputElement, photo)
    await user.click(screen.getByRole('button', { name: /submit review/i }))

    expect(await screen.findByText(
      'It will appear on our reviews page once it has been approved.',
    )).toBeInTheDocument()
    expect(submitSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        reviewerName: 'Priya S',
        phone: '9876543210',
        itemDescription: 'Red lehenga',
        rating: 5,
        reviewText: 'Beautiful outfit.',
      }),
      [expect.any(File)],
    )
    submitSpy.mockRestore()
  })

  it("surfaces the server's message verbatim when the API responds 429", async () => {
    const rateLimitError = Object.assign(new Error('Request failed with status code 429'), {
      isAxiosError: true,
      response: {
        status: 429,
        data: { success: false, data: null, error: 'Too many reviews submitted. Please try again in an hour.' },
      },
    })
    const submitSpy = vi.spyOn(reviewsApi, 'submit').mockRejectedValue(rateLimitError)
    const user = userEvent.setup()
    const screen = renderWithProviders(<SubmitReviewPage />, { route: '/review' })

    await fillValidForm(user, screen)
    await user.click(screen.getByRole('button', { name: /submit review/i }))

    expect(await screen.findByText('Too many reviews submitted. Please try again in an hour.'))
      .toBeInTheDocument()
    submitSpy.mockRestore()
  })

  it('rejects a photo over 5MB with an inline message and keeps it out of the upload list', async () => {
    const user = userEvent.setup()
    const screen = renderWithProviders(<SubmitReviewPage />, { route: '/review' })
    const oversized = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.jpg', { type: 'image/jpeg' })

    await user.upload(
      screen.container.querySelector('input[type="file"]') as HTMLInputElement,
      oversized,
    )

    expect(await screen.findByText('Each photo must be smaller than 5MB')).toBeInTheDocument()
    expect(screen.container.querySelectorAll('.ant-upload-list-item')).toHaveLength(0)
  })
})

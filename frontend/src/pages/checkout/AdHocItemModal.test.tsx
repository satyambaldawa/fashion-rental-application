import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import AdHocItemModal from './AdHocItemModal'
import type { AdHocCartItem } from '../../types/receipt'

function renderModal(onAdd: (item: AdHocCartItem) => void = () => {}) {
  render(<AdHocItemModal open rentalDays={3} onCancel={() => {}} onAdd={onAdd} />)
}

describe('AdHocItemModal', () => {
  it('emits an ad-hoc cart item with a generated lineKey', async () => {
    const onAdd = vi.fn()
    renderModal(onAdd)

    await userEvent.type(screen.getByLabelText(/product name/i), 'Walk-in Lehenga')
    await userEvent.type(screen.getByLabelText(/size/i), 'Free size')
    await userEvent.clear(screen.getByLabelText(/total price/i))
    await userEvent.type(screen.getByLabelText(/total price/i), '500')
    await userEvent.clear(screen.getByLabelText(/deposit/i))
    await userEvent.type(screen.getByLabelText(/deposit/i), '1000')
    await userEvent.click(screen.getByRole('button', { name: /add to cart/i }))

    expect(onAdd).toHaveBeenCalledTimes(1)
    expect(onAdd.mock.calls[0][0]).toMatchObject({
      kind: 'ADHOC', itemName: 'Walk-in Lehenga', size: 'Free size',
      flatPrice: 500, deposit: 1000, quantity: 1,
    })
    expect(onAdd.mock.calls[0][0].lineKey).toEqual(expect.any(String))
  })

  it('shows the derived per-day rate used for late fees', async () => {
    renderModal()

    await userEvent.clear(screen.getByLabelText(/total price/i))
    await userEvent.type(screen.getByLabelText(/total price/i), '500')

    expect(await screen.findByText(/₹167\/day/)).toBeInTheDocument()
  })

  it('refuses to submit without a product name, even when every other field is filled', async () => {
    const onAdd = vi.fn()
    renderModal(onAdd)

    await userEvent.clear(screen.getByLabelText(/total price/i))
    await userEvent.type(screen.getByLabelText(/total price/i), '500')
    await userEvent.click(screen.getByRole('button', { name: /add to cart/i }))

    expect(onAdd).not.toHaveBeenCalled()
  })

  it('refuses to submit a whitespace-only product name', async () => {
    const onAdd = vi.fn()
    renderModal(onAdd)

    await userEvent.type(screen.getByLabelText(/product name/i), '   ')
    await userEvent.clear(screen.getByLabelText(/total price/i))
    await userEvent.type(screen.getByLabelText(/total price/i), '500')
    await userEvent.click(screen.getByRole('button', { name: /add to cart/i }))

    expect(onAdd).not.toHaveBeenCalled()
  })
})

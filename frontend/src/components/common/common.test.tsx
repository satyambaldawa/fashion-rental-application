import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/render'
import { AmountDisplay } from './AmountDisplay'
import { ErrorMessage } from './ErrorMessage'
import { LoadingSpinner } from './LoadingSpinner'
import PageHeader from './PageHeader'
import ItemPhotoPlaceholder from './ItemPhotoPlaceholder'
import CustomerSearch from './CustomerSearch'

describe('common components', () => {
  it('AmountDisplay shows a formatted rupee amount', () => {
    render(<AmountDisplay amount={1500} />)
    expect(screen.getByText(/1[,.]?500/)).toBeInTheDocument()
  })

  it('ErrorMessage shows the message', () => {
    render(<ErrorMessage message="Something went wrong" />)
    expect(screen.getByText('Something went wrong')).toBeInTheDocument()
  })

  it('LoadingSpinner renders', () => {
    const { container } = render(<LoadingSpinner />)
    expect(container.firstChild).toBeTruthy()
  })

  it('PageHeader renders label, title and accent', () => {
    render(<PageHeader label="Inventory" title="Our" accent="Collection" count={3} />)
    expect(screen.getByText('Inventory')).toBeInTheDocument()
    expect(screen.getByText('Collection')).toBeInTheDocument()
  })

  it('ItemPhotoPlaceholder renders', () => {
    const { container } = render(<ItemPhotoPlaceholder />)
    expect(container.firstChild).toBeTruthy()
  })

  it('CustomerSearch renders a search input', () => {
    const { container } = renderWithProviders(<CustomerSearch onSelect={() => {}} />)
    expect(container.firstChild).toBeTruthy()
  })
})

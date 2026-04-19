import { describe, it, expect } from 'vitest'
import { formatCurrency } from './currency'

describe('formatCurrency', () => {
  it('should format 1500 rupees as ₹1,500', () => {
    expect(formatCurrency(1500)).toBe('₹1,500')
  })

  it('should format 0 rupees as ₹0', () => {
    expect(formatCurrency(0)).toBe('₹0')
  })

  it('should format 1 rupee as ₹1', () => {
    expect(formatCurrency(1)).toBe('₹1')
  })

  it('should format large amounts with Indian comma formatting', () => {
    expect(formatCurrency(100000)).toBe('₹1,00,000')
  })

  it('should format 500 rupees as ₹500', () => {
    expect(formatCurrency(500)).toBe('₹500')
  })
})

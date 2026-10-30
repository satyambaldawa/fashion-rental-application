import { describe, expect, it } from 'vitest'
import { buildUpiPaymentUri } from './upi'

describe('buildUpiPaymentUri', () => {
  it('builds the NPCI deep link with a two-decimal whole-rupee amount', () => {
    expect(buildUpiPaymentUri({ vpa: 'shop@ybl', payeeName: 'Shop', amountRupees: 1500, note: 'Rental' }))
      .toBe('upi://pay?pa=shop@ybl&pn=Shop&am=1500.00&cu=INR&tn=Rental')
  })

  it('percent-encodes spaces and ampersands in the payee name so the link is not cut short', () => {
    const uri = buildUpiPaymentUri({ vpa: 'shop@ybl', payeeName: 'Sharma & Sons', amountRupees: 10, note: 'Rental' })

    expect(uri).toContain('&pn=Sharma%20%26%20Sons&')
  })
})

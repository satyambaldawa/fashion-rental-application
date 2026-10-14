import type { InvoiceBackLink } from '../types/invoice'

export function isInvoiceBackLink(value: unknown): value is InvoiceBackLink {
  if (typeof value !== 'object' || value === null) return false
  const { backTo, backLabel } = value as Record<string, unknown>
  return typeof backTo === 'string' && typeof backLabel === 'string'
}

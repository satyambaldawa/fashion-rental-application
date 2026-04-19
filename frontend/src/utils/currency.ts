// All monetary amounts are stored and transferred as whole rupees (INTEGER).
// ₹150 is stored as 150, displayed as ₹150. No paise conversion.

export function formatCurrency(rupees: number): string {
  return `₹${rupees.toLocaleString('en-IN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 0
  })}`
}

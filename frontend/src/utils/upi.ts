export interface UpiPaymentRequest {
  vpa: string
  payeeName: string
  amountRupees: number
  note: string
}

// NPCI UPI deep link. `am` is a decimal amount; whole rupees are formatted as "1500.00" by string
// concatenation (never float math). `pn`/`tn` use encodeURIComponent, not URLSearchParams,
// which would encode spaces as "+" that some UPI apps display literally. `pa` is left as-is:
// the VPA is validated server-side to URI-safe characters, and "%40" for "@" is not reliably
// decoded by every UPI app.
export function buildUpiPaymentUri({ vpa, payeeName, amountRupees, note }: UpiPaymentRequest): string {
  return 'upi://pay'
    + `?pa=${vpa}`
    + `&pn=${encodeURIComponent(payeeName)}`
    + `&am=${amountRupees}.00`
    + '&cu=INR'
    + `&tn=${encodeURIComponent(note)}`
}

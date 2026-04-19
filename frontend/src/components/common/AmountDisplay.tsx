import { formatCurrency } from '../../utils/currency'

interface Props {
  amount: number
  className?: string
}

export function AmountDisplay({ amount, className }: Props) {
  return <span className={className}>{formatCurrency(amount)}</span>
}

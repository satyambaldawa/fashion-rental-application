import { Alert } from 'antd'
import type { CartCustomer } from '../../types/receipt'

interface CustomerCartBannerProps {
  customer: CartCustomer
}

export default function CustomerCartBanner({ customer }: CustomerCartBannerProps) {
  return (
    <Alert
      type="info"
      showIcon
      style={{ marginBottom: 16 }}
      message={<>Adding items for <strong>{customer.name}</strong> · {customer.phone}</>}
      description="A new receipt will be created for this customer only."
    />
  )
}

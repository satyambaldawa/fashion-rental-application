import type { CSSProperties } from 'react'
import { Button } from 'antd'
import { CheckOutlined } from '@ant-design/icons'
import { useMarkReceiptGiven } from '../../hooks/useMarkReceiptGiven'

interface MarkAsGivenButtonProps {
  receiptId: string
  size?: 'middle' | 'large'
  style?: CSSProperties
}

export default function MarkAsGivenButton({ receiptId, size = 'large', style }: MarkAsGivenButtonProps) {
  const { mutate, isPending } = useMarkReceiptGiven(receiptId)

  return (
    <Button type="primary" size={size} icon={<CheckOutlined />} loading={isPending} style={style} onClick={() => mutate()}>
      Mark as Given
    </Button>
  )
}

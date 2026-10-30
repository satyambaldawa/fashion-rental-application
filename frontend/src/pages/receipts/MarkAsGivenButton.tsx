import { useState, type CSSProperties } from 'react'
import { Button, Modal } from 'antd'
import { CheckOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { useMarkReceiptGiven } from '../../hooks/useMarkReceiptGiven'
import type { Receipt } from '../../types/receipt'

interface MarkAsGivenButtonProps {
  // Only what the confirmation needs, so both the detail page (Receipt) and list cards
  // (ReceiptSummary) can pass theirs.
  receipt: Pick<Receipt, 'id' | 'receiptNumber' | 'startDatetime'>
  size?: 'middle' | 'large'
  style?: CSSProperties
}

export default function MarkAsGivenButton({ receipt, size = 'large', style }: MarkAsGivenButtonProps) {
  const [isConfirmOpen, setIsConfirmOpen] = useState(false)
  const { mutate, isPending } = useMarkReceiptGiven(receipt.id)

  return (
    <>
      <Button
        type="primary"
        size={size}
        icon={<CheckOutlined />}
        style={style}
        onClick={() => setIsConfirmOpen(true)}
      >
        Mark as Given
      </Button>

      <Modal
        title="Mark as given?"
        open={isConfirmOpen}
        okText="Yes, mark as given"
        cancelText="Go back"
        confirmLoading={isPending}
        onOk={() => mutate(undefined, { onSuccess: () => setIsConfirmOpen(false) })}
        onCancel={() => setIsConfirmOpen(false)}
      >
        <p>
          Receipt <strong>{receipt.receiptNumber}</strong>
          <br />
          Booked start: <strong>{dayjs(receipt.startDatetime).format('DD MMM YYYY, h:mm A')}</strong>
        </p>
      </Modal>
    </>
  )
}

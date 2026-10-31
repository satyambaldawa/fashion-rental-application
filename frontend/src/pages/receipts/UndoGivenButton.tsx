import { useState } from 'react'
import { Button, Modal } from 'antd'
import { UndoOutlined } from '@ant-design/icons'
import { useUndoReceiptGiven } from '../../hooks/useUndoReceiptGiven'
import type { Receipt } from '../../types/receipt'

interface UndoGivenButtonProps {
  receipt: Pick<Receipt, 'id' | 'receiptNumber'>
}

export default function UndoGivenButton({ receipt }: UndoGivenButtonProps) {
  const [isConfirmOpen, setIsConfirmOpen] = useState(false)
  const { mutate, isPending } = useUndoReceiptGiven(receipt.id)

  return (
    <>
      <Button size="large" icon={<UndoOutlined />} onClick={() => setIsConfirmOpen(true)}>
        Undo Mark as Given
      </Button>

      <Modal
        title="Undo Mark as Given?"
        open={isConfirmOpen}
        okText="Yes, undo"
        cancelText="Go back"
        confirmLoading={isPending}
        onOk={() => mutate(undefined, { onSuccess: () => setIsConfirmOpen(false) })}
        onCancel={() => setIsConfirmOpen(false)}
      >
        <p>
          Receipt <strong>{receipt.receiptNumber}</strong> will go back to Booked: the dress is
          treated as still in the shop, and it can be cancelled again.
        </p>
      </Modal>
    </>
  )
}

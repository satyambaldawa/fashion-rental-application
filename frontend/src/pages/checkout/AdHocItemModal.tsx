import { Form, Input, InputNumber, Modal, Typography } from 'antd'
import type { AdHocCartItem } from '../../types/receipt'
import { formatCurrency } from '../../utils/currency'

interface AdHocItemModalProps {
  open: boolean
  rentalDays: number
  onCancel: () => void
  onAdd: (item: AdHocCartItem) => void
}

interface FormValues {
  itemName: string
  size?: string
  flatPrice: number
  deposit: number
  quantity: number
}

export default function AdHocItemModal({ open, rentalDays, onCancel, onAdd }: AdHocItemModalProps) {
  const [form] = Form.useForm<FormValues>()
  const flatPrice = Form.useWatch('flatPrice', form)

  // Mirrors CheckoutService#derivePerDayRate's ₹1 floor, so this preview never shows a
  // late-fee basis (e.g. ₹0/day) that the backend would never actually persist.
  const perDayRate =
    typeof flatPrice === 'number' ? Math.max(1, Math.round(flatPrice / Math.max(1, rentalDays))) : null

  async function handleSubmit() {
    let values: FormValues
    try {
      values = await form.validateFields()
    } catch {
      return // antd Form already renders the field errors inline
    }
    onAdd({
      kind: 'ADHOC',
      lineKey: crypto.randomUUID(),
      itemName: values.itemName.trim(),
      size: values.size?.trim() || null,
      flatPrice: values.flatPrice,
      deposit: values.deposit,
      quantity: values.quantity,
    })
    form.resetFields()
  }

  return (
    <Modal
      open={open}
      title="Add a product not in inventory"
      okText="Add to cart"
      onOk={handleSubmit}
      onCancel={() => { form.resetFields(); onCancel() }}
      destroyOnHidden
    >
      <Form form={form} layout="vertical" initialValues={{ deposit: 0, quantity: 1 }}>
        <Form.Item
          label="Product name"
          name="itemName"
          rules={[{ required: true, whitespace: true, message: 'Enter a product name' }]}
        >
          <Input autoFocus placeholder="e.g. Red Sherwani" />
        </Form.Item>

        <Form.Item label="Size" name="size">
          <Input placeholder="e.g. L, Free size" />
        </Form.Item>

        <Form.Item
          label="Total price for the whole rental"
          name="flatPrice"
          rules={[{ required: true, message: 'Enter the total price' }]}
          extra={
            perDayRate !== null ? (
              <Typography.Text type="secondary">
                {formatCurrency(perDayRate)}/day — used to calculate late fees
              </Typography.Text>
            ) : null
          }
        >
          <InputNumber min={1} max={1_000_000} precision={0} style={{ width: '100%' }} prefix="₹" />
        </Form.Item>

        <Form.Item
          label="Deposit"
          name="deposit"
          rules={[{ required: true, message: 'Enter a deposit (0 if none)' }]}
        >
          <InputNumber min={0} max={1_000_000} precision={0} style={{ width: '100%' }} prefix="₹" />
        </Form.Item>

        <Form.Item
          label="Quantity"
          name="quantity"
          rules={[{ required: true, message: 'Enter a quantity' }]}
        >
          <InputNumber min={1} max={100} precision={0} style={{ width: '100%' }} />
        </Form.Item>
      </Form>
    </Modal>
  )
}

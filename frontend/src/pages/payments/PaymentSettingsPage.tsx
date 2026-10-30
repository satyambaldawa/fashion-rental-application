import { useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Form, Input, Typography, message } from 'antd'
import { paymentSettingsApi } from '../../api/paymentSettings'
import type { UpdatePaymentSettingsRequest } from '../../types/paymentSettings'

const { Text, Paragraph } = Typography

// Mirrors UpdatePaymentSettingsRequest.UPI_VPA_PATTERN on the server (handle@psp).
const UPI_VPA_PATTERN = /^[a-zA-Z0-9._-]{2,256}@[a-zA-Z]{2,64}$/

export default function PaymentSettingsPage() {
  const queryClient = useQueryClient()
  const [form] = Form.useForm<UpdatePaymentSettingsRequest>()
  const upiVpa = Form.useWatch('upiVpa', form)

  const { data: settings, isLoading } = useQuery({
    queryKey: ['payment-settings'],
    queryFn: paymentSettingsApi.get,
  })

  useEffect(() => {
    if (!settings) return
    form.setFieldsValue({ upiVpa: settings.upiVpa ?? '', upiPayeeName: settings.upiPayeeName ?? '' })
  }, [settings, form])

  const { mutate: save, isPending } = useMutation({
    mutationFn: paymentSettingsApi.update,
    onSuccess: (saved) => {
      queryClient.setQueryData(['payment-settings'], saved)
      queryClient.invalidateQueries({ queryKey: ['checkout-payment-options'] })
      message.success(saved.upiEnabled ? 'UPI payments enabled.' : 'UPI payments turned off.')
    },
    onError: (err: { response?: { data?: { error?: string } } }) => {
      message.error(err.response?.data?.error ?? 'Failed to save payment settings.')
    },
  })

  const upiBeingSet = !!upiVpa?.trim()

  return (
    <div style={{ maxWidth: 480 }}>
      <Paragraph type="secondary">
        When a UPI ID is set, staff can choose UPI at checkout and show the customer a QR code for
        the full amount. Leave it blank to offer cash only.
      </Paragraph>

      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        message="Customers' money goes to this UPI ID. After saving, pay ₹1 to it from your own phone to confirm the name shown is your shop."
      />

      <Form
        form={form}
        layout="vertical"
        disabled={isLoading}
        onFinish={(values) => save({ upiVpa: values.upiVpa?.trim() ?? '', upiPayeeName: values.upiPayeeName?.trim() ?? '' })}
      >
        <Form.Item
          label="UPI ID"
          name="upiVpa"
          extra={<Text type="secondary">A business UPI ID, e.g. shopname@ybl</Text>}
          rules={[{
            validator: (_, value: string | undefined) =>
              !value?.trim() || UPI_VPA_PATTERN.test(value.trim())
                ? Promise.resolve()
                : Promise.reject(new Error('Enter a valid UPI ID, e.g. shopname@ybl')),
          }]}
        >
          <Input placeholder="shopname@ybl" autoComplete="off" />
        </Form.Item>

        <Form.Item
          label="Payee name"
          name="upiPayeeName"
          extra={<Text type="secondary">Shown to the customer in their UPI app</Text>}
          rules={[{ required: upiBeingSet, whitespace: true, message: 'Payee name is required when a UPI ID is set' }, { max: 100 }]}
        >
          <Input placeholder="Manisha's Fancy Collection" />
        </Form.Item>

        <Button type="primary" htmlType="submit" loading={isPending}>
          Save
        </Button>
      </Form>
    </div>
  )
}

import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Form,
  Input,
  Radio,
  Button,
  Space,
  Typography,
  Alert,
  Spin,
} from 'antd'
import { customersApi } from '../../api/customers'
import type { UpdateCustomerRequest, CustomerType } from '../../types/customer'

interface EditCustomerForm {
  name: string
  phone: string
  address?: string
  customerType: CustomerType
  organizationName?: string
}

export default function EditCustomerPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data: customer, isLoading, isError } = useQuery({
    queryKey: ['customers', id],
    queryFn: () => customersApi.get(id!),
  })

  const updateMutation = useMutation({
    mutationFn: (data: UpdateCustomerRequest) => customersApi.update(id!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers'] })
      navigate('/customers')
    },
  })

  if (isLoading) {
    return <Spin />
  }

  if (isError || !customer) {
    return (
      <Alert
        type="error"
        message="Failed to load customer. Please go back and try again."
      />
    )
  }

  return <EditCustomerForm key={id} customer={customer} onSubmit={updateMutation.mutate} isPending={updateMutation.isPending} isError={updateMutation.isError} error={updateMutation.error} onCancel={() => navigate('/customers')} />
}

interface EditCustomerFormProps {
  customer: { name: string; phone: string; address: string | null; customerType: CustomerType; organizationName: string | null }
  onSubmit: (data: UpdateCustomerRequest) => void
  isPending: boolean
  isError: boolean
  error: unknown
  onCancel: () => void
}

function EditCustomerForm({ customer, onSubmit, isPending, isError, error, onCancel }: EditCustomerFormProps) {
  const [selectedType, setSelectedType] = useState<CustomerType>(customer.customerType)
  const [form] = Form.useForm<EditCustomerForm>()

  function handleSubmit(values: EditCustomerForm) {
    onSubmit({
      name: values.name,
      phone: values.phone,
      address: values.address || undefined,
      customerType: values.customerType,
      organizationName: values.organizationName || undefined,
    })
  }

  const apiError = error as { response?: { data?: { error?: string } } } | null
  const errorMessage = apiError?.response?.data?.error ?? 'Update failed. Please try again.'

  return (
    <div style={{ maxWidth: 500 }}>
      <Typography.Title level={4}>Edit Customer</Typography.Title>

      <Form
        form={form}
        layout="vertical"
        onFinish={handleSubmit}
        initialValues={{
          name: customer.name,
          phone: customer.phone,
          address: customer.address ?? undefined,
          customerType: customer.customerType,
          organizationName: customer.organizationName ?? undefined,
        }}
      >
        <Form.Item
          name="name"
          label="Name"
          rules={[{ required: true, message: 'Name is required' }]}
        >
          <Input placeholder="Full name" />
        </Form.Item>

        <Form.Item
          name="phone"
          label="Phone Number"
          rules={[
            { required: true, message: 'Phone number is required' },
            {
              pattern: /^[6-9]\d{9}$/,
              message: 'Enter a valid 10-digit Indian mobile number',
            },
          ]}
        >
          <Input placeholder="e.g. 9876543210" maxLength={10} />
        </Form.Item>

        <Form.Item
          name="customerType"
          label="Customer Type"
          rules={[{ required: true, message: 'Customer type is required' }]}
        >
          <Radio.Group onChange={e => setSelectedType(e.target.value)}>
            <Radio value="STUDENT">Student</Radio>
            <Radio value="PROFESSIONAL">Professional</Radio>
            <Radio value="MISC">Misc</Radio>
          </Radio.Group>
        </Form.Item>

        {selectedType === 'STUDENT' && (
          <Form.Item
            name="organizationName"
            label="School Name"
            rules={[{ required: true, message: 'School name is required for students' }]}
          >
            <Input placeholder="School or college name" />
          </Form.Item>
        )}

        {selectedType === 'PROFESSIONAL' && (
          <Form.Item
            name="organizationName"
            label="Organization Name"
            rules={[{ required: true, message: 'Organization name is required for professionals' }]}
          >
            <Input placeholder="Company or organization name" />
          </Form.Item>
        )}

        <Form.Item name="address" label="Address">
          <Input.TextArea rows={3} placeholder="Optional address" />
        </Form.Item>

        {isError && (
          <Alert type="error" message={errorMessage} style={{ marginBottom: 16 }} />
        )}

        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit" loading={isPending}>
              Save Changes
            </Button>
            <Button onClick={onCancel}>Cancel</Button>
          </Space>
        </Form.Item>
      </Form>
    </div>
  )
}

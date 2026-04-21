import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import {
  Form,
  Input,
  Radio,
  Button,
  Space,
  Typography,
  Alert,
} from 'antd'
import { UserOutlined } from '@ant-design/icons'
import { customersApi } from '../../api/customers'
import type { CreateCustomerRequest, CustomerType, Customer } from '../../types/customer'

type Phase = 'phone-check' | 'registration' | 'success'

interface PhoneCheckForm {
  phone: string
}

interface RegistrationForm {
  name: string
  customerType: CustomerType
  organizationName?: string
  address?: string
}

export default function RegisterCustomerPage() {
  const navigate = useNavigate()
  const [phase, setPhase] = useState<Phase>('phone-check')
  const [phone, setPhone] = useState('')
  const [existingCustomerName, setExistingCustomerName] = useState<string | null>(null)
  const [registeredCustomer, setRegisteredCustomer] = useState<Customer | null>(null)
  const [selectedType, setSelectedType] = useState<CustomerType>('MISC')

  const [phoneForm] = Form.useForm<PhoneCheckForm>()
  const [regForm] = Form.useForm<RegistrationForm>()

  const checkPhoneMutation = useMutation({
    mutationFn: (p: string) => customersApi.search({ phone: p }),
    onSuccess: (results) => {
      const exactMatch = results.find(r => r.phone === phone)
      if (exactMatch) {
        setExistingCustomerName(exactMatch.name)
      } else {
        setExistingCustomerName(null)
        setPhase('registration')
      }
    },
  })

  const registerMutation = useMutation({
    mutationFn: (data: CreateCustomerRequest) => customersApi.create(data),
    onSuccess: (customer) => {
      setRegisteredCustomer(customer)
      setPhase('success')
    },
  })

  function handlePhoneCheck(values: PhoneCheckForm) {
    setPhone(values.phone)
    checkPhoneMutation.mutate(values.phone)
  }

  function handleRegistration(values: RegistrationForm) {
    registerMutation.mutate({
      name: values.name,
      phone,
      address: values.address || undefined,
      customerType: values.customerType,
      organizationName: values.organizationName || undefined,
    })
  }

  function handleStartOver() {
    setPhase('phone-check')
    setPhone('')
    setExistingCustomerName(null)
    setRegisteredCustomer(null)
    phoneForm.resetFields()
    regForm.resetFields()
    setSelectedType('MISC')
  }

  if (phase === 'success' && registeredCustomer) {
    return (
      <div style={{ maxWidth: 500 }}>
        <Typography.Title level={4}>Customer Registered</Typography.Title>
        <Alert
          type="success"
          message={`${registeredCustomer.name} has been registered successfully.`}
          style={{ marginBottom: 24 }}
        />
        <Space>
          <Button type="primary" onClick={handleStartOver}>
            Register Another
          </Button>
          <Button onClick={() => navigate('/customers')}>
            Go to Customers
          </Button>
        </Space>
      </div>
    )
  }

  if (phase === 'registration') {
    return (
      <div style={{ maxWidth: 500 }}>
        <Typography.Title level={4}>Register New Customer</Typography.Title>

        <Form
          form={regForm}
          layout="vertical"
          onFinish={handleRegistration}
          initialValues={{ customerType: 'MISC' }}
        >
          <Form.Item label="Phone">
            <Input value={phone} readOnly />
          </Form.Item>

          <Form.Item
            name="name"
            label="Name"
            rules={[{ required: true, message: 'Name is required' }]}
          >
            <Input placeholder="Full name" />
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

          {registerMutation.isError && (
            <Alert
              type="error"
              message="Registration failed. Please try again."
              style={{ marginBottom: 16 }}
            />
          )}

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" loading={registerMutation.isPending}>
                Register Customer
              </Button>
              <Button onClick={handleStartOver}>Start Over</Button>
            </Space>
          </Form.Item>
        </Form>
      </div>
    )
  }

  return (
    <div style={{ maxWidth: 500 }}>
      <Typography.Title level={4}>Register New Customer</Typography.Title>
      <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 24 }}>
        Enter the customer's phone number to check if they are already registered.
      </Typography.Text>

      <Form form={phoneForm} layout="vertical" onFinish={handlePhoneCheck}>
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
          <Input
            prefix={<UserOutlined />}
            placeholder="e.g. 9876543210"
            maxLength={10}
          />
        </Form.Item>

        {existingCustomerName && (
          <Alert
            type="warning"
            message={`Customer already exists: ${existingCustomerName}`}
            description={
              <Button
                type="link"
                style={{ padding: 0 }}
                onClick={() => navigate('/customers')}
              >
                View Profile
              </Button>
            }
            style={{ marginBottom: 16 }}
          />
        )}

        {checkPhoneMutation.isError && (
          <Alert
            type="error"
            message="Failed to check phone. Please try again."
            style={{ marginBottom: 16 }}
          />
        )}

        <Form.Item>
          <Button type="primary" htmlType="submit" loading={checkPhoneMutation.isPending}>
            Check
          </Button>
        </Form.Item>
      </Form>
    </div>
  )
}

import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
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
import { UserOutlined, CheckCircleFilled } from '@ant-design/icons'
import PageHeader from '../../components/common/PageHeader'
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
  const [searchParams] = useSearchParams()
  const returnTo = searchParams.get('returnTo')
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
      if (returnTo === 'checkout') {
        navigate(`/checkout?newCustomerId=${customer.id}`)
        return
      }
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
        <PageHeader label="Customers" title="Customer" accent="Registered" />
        <div style={{
          display: 'flex',
          alignItems: 'flex-start',
          gap: 12,
          background: '#FBF1F5',
          border: '1px solid #eed6e0',
          borderRadius: 14,
          padding: 20,
          marginBottom: 24,
        }}>
          <CheckCircleFilled style={{ color: '#A81259', fontSize: 20, marginTop: 2 }} />
          <div>
            <Typography.Text strong style={{ display: 'block', color: '#33101F' }}>
              {registeredCustomer.name}
            </Typography.Text>
            <Typography.Text type="secondary">
              has been registered successfully.
            </Typography.Text>
          </div>
        </div>
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
        <PageHeader label="Customers" title="Register" accent="Customer" />
        <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 24 }}>
          Phone verified: {phone}
        </Typography.Text>

        {/* Keyed so React never reuses the phone-check phase's Form instance: both
            phases render the same element shapes in the same positions, and without a
            distinct key antd would skip mounting this form — leaving initialValues
            unapplied and every submit failing on "Customer type is required". */}
        <Form
          key="registration"
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
              showIcon
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
      <PageHeader label="Customers" title="Register" accent="Customer" />
      <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 24 }}>
        Enter the customer's phone number to check if they are already registered.
      </Typography.Text>

      <Form key="phone-check" form={phoneForm} layout="vertical" onFinish={handlePhoneCheck}>
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
            showIcon
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
            showIcon
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

import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Button, Input, Typography, Badge, Card, Space, Tag, Alert, Spin } from 'antd'
import { PlusOutlined, UserOutlined } from '@ant-design/icons'
import { customersApi } from '../../api/customers'
import { useDebounce } from '../../hooks/useDebounce'
import type { CustomerType } from '../../types/customer'

const CUSTOMER_TYPE_LABELS: Record<CustomerType, string> = {
  STUDENT: 'Student',
  PROFESSIONAL: 'Professional',
  MISC: 'Misc',
}

const CUSTOMER_TYPE_COLORS: Record<CustomerType, string> = {
  STUDENT: 'blue',
  PROFESSIONAL: 'green',
  MISC: 'default',
}

export default function CustomersPage() {
  const navigate = useNavigate()
  const [searchInput, setSearchInput] = useState('')
  const debouncedSearch = useDebounce(searchInput, 300)

  const isPhoneSearch = /^\d+$/.test(debouncedSearch)
  const searchParam = debouncedSearch.length >= 3
    ? (isPhoneSearch ? { phone: debouncedSearch } : { name: debouncedSearch })
    : null

  const { data: customers, isLoading, isError } = useQuery({
    queryKey: ['customers', 'search', searchParam],
    queryFn: () => customersApi.search(searchParam!),
    enabled: searchParam !== null,
  })

  const showPrompt = debouncedSearch.length < 3
  const showEmpty = searchParam !== null && !isLoading && customers?.length === 0

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>Customers</Typography.Title>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => navigate('/customers/register')}
        >
          Register New Customer
        </Button>
      </div>

      <Input
        prefix={<UserOutlined />}
        placeholder="Search by name or phone (min 3 characters)"
        value={searchInput}
        onChange={e => setSearchInput(e.target.value)}
        allowClear
        style={{ marginBottom: 24, maxWidth: 480 }}
      />

      {showPrompt && (
        <Typography.Text type="secondary">
          Start typing to search customers (minimum 3 characters).
        </Typography.Text>
      )}

      {isLoading && <Spin />}

      {isError && (
        <Alert type="error" message="Failed to load customers. Please try again." />
      )}

      {showEmpty && (
        <Typography.Text type="secondary">No customers found for your search.</Typography.Text>
      )}

      {customers && customers.length > 0 && (
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          {customers.map(customer => (
            <Card
              key={customer.id}
              size="small"
              hoverable
              onClick={() => navigate(`/customers/${customer.id}`)}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <Typography.Text strong>{customer.name}</Typography.Text>
                  <div style={{ color: '#666', fontSize: 13, marginTop: 2 }}>{customer.phone}</div>
                  {customer.organizationName && (
                    <div style={{ color: '#666', fontSize: 13 }}>{customer.organizationName}</div>
                  )}
                </div>
                <Space>
                  <Tag color={CUSTOMER_TYPE_COLORS[customer.customerType]}>
                    {CUSTOMER_TYPE_LABELS[customer.customerType]}
                  </Tag>
                  {customer.activeRentalsCount > 0 && (
                    <Badge count={customer.activeRentalsCount} color="blue" title="Active rentals" />
                  )}
                </Space>
              </div>
            </Card>
          ))}
        </Space>
      )}
    </div>
  )
}

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Table, Button, InputNumber, Alert, Space, Typography, Popconfirm, message, Form, Input, Select, Card, Divider } from 'antd'
import { PlusOutlined, DeleteOutlined, UserAddOutlined } from '@ant-design/icons'
import PageHeader from '../components/common/PageHeader'
import { getLateFeeRules, updateLateFeeRules } from '../api/config'
import { authApi } from '../api/auth'
import type { LateFeeRuleItem } from '../types/config'
import type { CreateUserRequest } from '../types/auth'

const { Text } = Typography

type EditableRule = LateFeeRuleItem & { key: string }

function toEditableRules(rules: LateFeeRuleItem[]): EditableRule[] {
  return rules.map((r, i) => ({ ...r, key: r.id ?? `new-${i}` }))
}

export default function SettingsPage() {
  const queryClient = useQueryClient()
  const [rows, setRows] = useState<EditableRule[] | null>(null)
  const [validationError, setValidationError] = useState<string | null>(null)
  const [userForm] = Form.useForm<CreateUserRequest>()

  const { data: savedRules, isLoading } = useQuery({
    queryKey: ['late-fee-rules'],
    queryFn: getLateFeeRules,
    select: (data) => data,
  })

  // Initialise local rows from server data on first load
  const editableRows: EditableRule[] = rows ?? toEditableRules(savedRules ?? [])

  const { mutate: save, isPending: isSaving } = useMutation({
    mutationFn: updateLateFeeRules,
    onSuccess: (updated) => {
      queryClient.setQueryData(['late-fee-rules'], updated)
      setRows(toEditableRules(updated))
      setValidationError(null)
      message.success('Late fee rules saved.')
    },
    onError: (err: { response?: { data?: { error?: string } } }) => {
      setValidationError(err.response?.data?.error ?? 'Failed to save rules.')
    },
  })

  const { mutate: createUser, isPending: isCreatingUser } = useMutation({
    mutationFn: (data: CreateUserRequest) => authApi.createUser(data),
    onSuccess: () => {
      message.success('User created successfully.')
      userForm.resetFields()
    },
    onError: (err: { response?: { data?: { error?: string } } }) => {
      message.error(err.response?.data?.error ?? 'Failed to create user.')
    },
  })

  function updateRow(key: string, field: keyof EditableRule, value: number | null) {
    setRows(editableRows.map((r) => (r.key === key ? { ...r, [field]: value } : r)))
  }

  function addRow() {
    const nextSort = editableRows.length + 1
    const newRow: EditableRule = {
      key: `new-${Date.now()}`,
      id: null,
      durationFromHours: 0,
      durationToHours: null,
      penaltyMultiplier: 1.0,
      sortOrder: nextSort,
      isActive: true,
    }
    setRows([...editableRows, newRow])
  }

  function removeRow(key: string) {
    const updated = editableRows
      .filter((r) => r.key !== key)
      .map((r, i) => ({ ...r, sortOrder: i + 1 }))
    setRows(updated)
  }

  function handleSave() {
    setValidationError(null)
    save({ rules: editableRows.map(({ key: _key, ...r }) => r) })
  }

  function handleCreateUser(values: CreateUserRequest) {
    createUser(values)
  }

  const columns = [
    {
      title: 'From (hrs)',
      dataIndex: 'durationFromHours',
      width: 120,
      render: (val: number, row: EditableRule) => (
        <InputNumber
          min={0}
          value={val}
          onChange={(v) => updateRow(row.key, 'durationFromHours', v ?? 0)}
          style={{ width: '100%' }}
        />
      ),
    },
    {
      title: 'To (hrs)',
      dataIndex: 'durationToHours',
      width: 120,
      render: (val: number | null, row: EditableRule) => (
        <InputNumber
          min={1}
          value={val ?? undefined}
          placeholder="∞"
          onChange={(v) => updateRow(row.key, 'durationToHours', v ?? null)}
          style={{ width: '100%' }}
        />
      ),
    },
    {
      title: 'Multiplier',
      dataIndex: 'penaltyMultiplier',
      width: 130,
      render: (val: number, row: EditableRule) => (
        <InputNumber
          min={0.1}
          step={0.25}
          precision={2}
          value={val}
          formatter={(v) => `${v}x`}
          parser={(v) => parseFloat((v ?? '').replace('x', ''))}
          onChange={(v) => updateRow(row.key, 'penaltyMultiplier', v ?? 0.1)}
          style={{ width: '100%' }}
        />
      ),
    },
    {
      title: 'Label Preview',
      dataIndex: 'label',
      render: (_: unknown, row: EditableRule) => (
        <Text type="secondary">{computeLabel(row.durationFromHours, row.durationToHours)}</Text>
      ),
    },
    {
      title: '',
      width: 60,
      render: (_: unknown, row: EditableRule) => (
        <Popconfirm title="Remove this tier?" onConfirm={() => removeRow(row.key)}>
          <Button icon={<DeleteOutlined />} type="text" danger size="small" />
        </Popconfirm>
      ),
    },
  ]

  return (
    <div style={{ maxWidth: 700 }}>
      <PageHeader label="Settings" title="Late Fee" accent="Rules" />
      <Alert
        type="warning"
        showIcon
        message="These values apply to all future return calculations. Changes do not affect invoices already generated."
        style={{ marginBottom: 16 }}
      />

      {validationError && (
        <Alert type="error" message={validationError} style={{ marginBottom: 12 }} closable onClose={() => setValidationError(null)} />
      )}

      <Table
        columns={columns}
        dataSource={editableRows}
        pagination={false}
        loading={isLoading}
        rowKey="key"
        size="small"
        scroll={{ x: 'max-content' }}
        style={{ marginBottom: 16 }}
      />

      <Space>
        <Button icon={<PlusOutlined />} onClick={addRow}>Add Tier</Button>
        <Button type="primary" loading={isSaving} onClick={handleSave}>Save Rules</Button>
      </Space>

      <Divider />

      <Card
        title={
          <span>
            <UserAddOutlined style={{ marginRight: 8 }} />
            Manage Users
          </span>
        }
        style={{ marginTop: 8 }}
      >
        <Form
          form={userForm}
          layout="vertical"
          onFinish={handleCreateUser}
          initialValues={{ role: 'EXECUTIVE' }}
          style={{ maxWidth: 400 }}
        >
          <Form.Item
            name="username"
            label="Username"
            rules={[{ required: true, message: 'Username is required' }]}
          >
            <Input placeholder="e.g. staff01" autoComplete="off" />
          </Form.Item>
          <Form.Item
            name="password"
            label="Password"
            rules={[
              { required: true, message: 'Password is required' },
              { min: 6, message: 'Password must be at least 6 characters' },
            ]}
          >
            <Input.Password placeholder="Min 6 characters" autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="role" label="Role">
            <Select>
              <Select.Option value="EXECUTIVE">Executive (Staff)</Select.Option>
              <Select.Option value="OWNER">Owner</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              loading={isCreatingUser}
              icon={<UserAddOutlined />}
            >
              Create User
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  )
}

function computeLabel(fromHours: number, toHours: number | null): string {
  const from = formatHours(fromHours)
  if (toHours === null) return `${from}+`
  return `${from}–${formatHours(toHours)}`
}

function formatHours(hours: number): string {
  if (hours === 0) return '0 hrs'
  if (hours < 24) return `${hours} hrs`
  const days = Math.floor(hours / 24)
  return `${days} ${days === 1 ? 'day' : 'days'}`
}

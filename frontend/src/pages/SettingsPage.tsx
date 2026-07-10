import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Table, Button, InputNumber, Alert, Space, Typography, Popconfirm, message,
  Form, Input, Select, Card, Divider, Tag, Modal,
} from 'antd'
import { PlusOutlined, DeleteOutlined, UserAddOutlined, EditOutlined } from '@ant-design/icons'
import PageHeader from '../components/common/PageHeader'
import { getLateFeeRules, updateLateFeeRules } from '../api/config'
import { authApi } from '../api/auth'
import type { LateFeeRuleItem } from '../types/config'
import type { CreateUserRequest, UpdateUserRequest, UserRecord } from '../types/auth'

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
  const [editForm] = Form.useForm<UpdateUserRequest>()
  const [editingUser, setEditingUser] = useState<UserRecord | null>(null)

  const { data: savedRules, isLoading } = useQuery({
    queryKey: ['late-fee-rules'],
    queryFn: getLateFeeRules,
  })

  const editableRows: EditableRule[] = rows ?? toEditableRules(savedRules ?? [])

  const { data: users, isLoading: usersLoading } = useQuery({
    queryKey: ['users'],
    queryFn: authApi.listUsers,
  })

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
      queryClient.invalidateQueries({ queryKey: ['users'] })
    },
    onError: (err: { response?: { data?: { error?: string } } }) => {
      message.error(err.response?.data?.error ?? 'Failed to create user.')
    },
  })

  const { mutate: updateUser, isPending: isUpdatingUser } = useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateUserRequest }) =>
      authApi.updateUser(id, data),
    onSuccess: () => {
      message.success('User updated successfully.')
      setEditingUser(null)
      queryClient.invalidateQueries({ queryKey: ['users'] })
    },
    onError: (err: { response?: { data?: { error?: string } } }) => {
      message.error(err.response?.data?.error ?? 'Failed to update user.')
    },
  })

  const { mutate: deleteUser } = useMutation({
    mutationFn: (id: string) => authApi.deleteUser(id),
    onSuccess: () => {
      message.success('User deactivated.')
      queryClient.invalidateQueries({ queryKey: ['users'] })
    },
    onError: () => message.error('Failed to deactivate user.'),
  })

  function updateRow(key: string, field: keyof EditableRule, value: number | null) {
    setRows(editableRows.map((r) => (r.key === key ? { ...r, [field]: value } : r)))
  }

  function addRow() {
    const newRow: EditableRule = {
      key: `new-${Date.now()}`,
      id: null,
      durationFromHours: 0,
      durationToHours: null,
      penaltyMultiplier: 1.0,
      sortOrder: editableRows.length + 1,
      isActive: true,
    }
    setRows([...editableRows, newRow])
  }

  function removeRow(key: string) {
    setRows(editableRows
      .filter((r) => r.key !== key)
      .map((r, i) => ({ ...r, sortOrder: i + 1 })))
  }

  function handleSave() {
    setValidationError(null)
    save({ rules: editableRows.map(({ key: _key, ...r }) => r) })
  }

  function openEditModal(user: UserRecord) {
    setEditingUser(user)
    editForm.setFieldsValue({ role: user.role, password: '' })
  }

  function handleEditSubmit(values: UpdateUserRequest) {
    if (!editingUser) return
    updateUser({
      id: editingUser.id,
      data: {
        role: values.role,
        password: values.password || undefined,
      },
    })
  }

  const lateFeeColumns = [
    {
      title: 'From (hrs)',
      dataIndex: 'durationFromHours',
      width: 120,
      render: (val: number, row: EditableRule) => (
        <InputNumber min={0} value={val}
          onChange={(v) => updateRow(row.key, 'durationFromHours', v ?? 0)}
          style={{ width: '100%' }} />
      ),
    },
    {
      title: 'To (hrs)',
      dataIndex: 'durationToHours',
      width: 120,
      render: (val: number | null, row: EditableRule) => (
        <InputNumber min={1} value={val ?? undefined} placeholder="∞"
          onChange={(v) => updateRow(row.key, 'durationToHours', v ?? null)}
          style={{ width: '100%' }} />
      ),
    },
    {
      title: 'Multiplier',
      dataIndex: 'penaltyMultiplier',
      width: 130,
      render: (val: number, row: EditableRule) => (
        <InputNumber min={0.1} step={0.25} precision={2} value={val}
          formatter={(v) => `${v}x`}
          parser={(v) => parseFloat((v ?? '').replace('x', ''))}
          onChange={(v) => updateRow(row.key, 'penaltyMultiplier', v ?? 0.1)}
          style={{ width: '100%' }} />
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

  const userColumns = [
    {
      title: 'Username',
      dataIndex: 'username',
      key: 'username',
      render: (name: string) => <Text strong>{name}</Text>,
    },
    {
      title: 'Role',
      dataIndex: 'role',
      key: 'role',
      render: (role: string) => (
        <Tag color={role === 'OWNER' ? 'volcano' : 'blue'}>
          {role === 'OWNER' ? 'Owner' : 'Executive'}
        </Tag>
      ),
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string) => new Date(date).toLocaleDateString('en-IN'),
    },
    {
      title: '',
      key: 'actions',
      width: 100,
      render: (_: unknown, user: UserRecord) => (
        <Space>
          <Button
            size="small"
            icon={<EditOutlined />}
            onClick={() => openEditModal(user)}
          />
          <Popconfirm
            title={`Deactivate ${user.username}?`}
            description="They will no longer be able to log in."
            onConfirm={() => deleteUser(user.id)}
            okText="Deactivate"
            okButtonProps={{ danger: true }}
          >
            <Button size="small" icon={<DeleteOutlined />} danger />
          </Popconfirm>
        </Space>
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
        <Alert type="error" message={validationError} style={{ marginBottom: 12 }}
          closable onClose={() => setValidationError(null)} />
      )}

      <Table
        columns={lateFeeColumns}
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
        title={<span><UserAddOutlined style={{ marginRight: 8 }} />Manage Users</span>}
        style={{ marginTop: 8 }}
      >
        {/* Existing users table */}
        <Table
          columns={userColumns}
          dataSource={users ?? []}
          loading={usersLoading}
          rowKey="id"
          size="small"
          pagination={false}
          style={{ marginBottom: 24 }}
        />

        <Divider orientation="left" plain>Add New User</Divider>

        <Form
          form={userForm}
          layout="vertical"
          onFinish={(values: CreateUserRequest) => createUser(values)}
          initialValues={{ role: 'EXECUTIVE' }}
          style={{ maxWidth: 400 }}
        >
          <Form.Item name="username" label="Username"
            rules={[{ required: true, message: 'Username is required' }]}>
            <Input placeholder="e.g. staff01" autoComplete="off" />
          </Form.Item>
          <Form.Item name="password" label="Password"
            rules={[
              { required: true, message: 'Password is required' },
              { min: 6, message: 'Password must be at least 6 characters' },
            ]}>
            <Input.Password placeholder="Min 6 characters" autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="role" label="Role">
            <Select>
              <Select.Option value="EXECUTIVE">Executive (Staff)</Select.Option>
              <Select.Option value="OWNER">Owner</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={isCreatingUser} icon={<UserAddOutlined />}>
              Create User
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {/* Edit user modal */}
      <Modal
        title={`Edit — ${editingUser?.username}`}
        open={!!editingUser}
        onCancel={() => setEditingUser(null)}
        onOk={() => editForm.submit()}
        okText="Save Changes"
        confirmLoading={isUpdatingUser}
      >
        <Form form={editForm} layout="vertical" onFinish={handleEditSubmit} style={{ marginTop: 16 }}>
          <Form.Item name="role" label="Role">
            <Select>
              <Select.Option value="EXECUTIVE">Executive (Staff)</Select.Option>
              <Select.Option value="OWNER">Owner</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="password" label="New Password"
            rules={[{ min: 6, message: 'Password must be at least 6 characters' }]}
            extra="Leave blank to keep current password">
            <Input.Password placeholder="Min 6 characters" autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
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

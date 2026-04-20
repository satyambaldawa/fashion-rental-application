import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Table, Button, InputNumber, Alert, Space, Typography, Popconfirm, message } from 'antd'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons'
import { getLateFeeRules, updateLateFeeRules } from '../api/config'
import type { LateFeeRuleItem } from '../types/config'

const { Title, Text } = Typography

type EditableRule = LateFeeRuleItem & { key: string }

function toEditableRules(rules: LateFeeRuleItem[]): EditableRule[] {
  return rules.map((r, i) => ({ ...r, key: r.id ?? `new-${i}` }))
}

export default function SettingsPage() {
  const queryClient = useQueryClient()
  const [rows, setRows] = useState<EditableRule[] | null>(null)
  const [validationError, setValidationError] = useState<string | null>(null)

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
      <Title level={4} style={{ marginBottom: 4 }}>Late Fee Rules</Title>
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
        style={{ marginBottom: 16 }}
      />

      <Space>
        <Button icon={<PlusOutlined />} onClick={addRow}>Add Tier</Button>
        <Button type="primary" loading={isSaving} onClick={handleSave}>Save Rules</Button>
      </Space>
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

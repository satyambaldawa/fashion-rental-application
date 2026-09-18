import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Table, Button, InputNumber, Space, Typography, Popconfirm, message,
  Form, Input, Select, Tag, Modal, DatePicker, Switch,
} from 'antd'
import { PlusOutlined, EditOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import PageHeader from '../../components/common/PageHeader'
import { couponsApi } from '../../api/coupons'
import { formatCurrency } from '../../utils/currency'
import { toApiDatetime } from '../../utils/datetime'
import type { Coupon, CreateCouponRequest, DiscountType } from '../../types/coupons'

const { Text } = Typography

interface CouponFormValues {
  code: string
  discountType: DiscountType
  value: number
  minSubtotal: number | null
  validRange: [Dayjs, Dayjs]
  usageLimit: number | null
}

function formatValidity(validFrom: string, validTo: string): string {
  return `${dayjs(validFrom).format('DD MMM YYYY')} – ${dayjs(validTo).format('DD MMM YYYY')}`
}

export default function CouponsPage() {
  const queryClient = useQueryClient()
  const [includeInactive, setIncludeInactive] = useState(true)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingCoupon, setEditingCoupon] = useState<Coupon | null>(null)
  const [form] = Form.useForm<CouponFormValues>()
  const discountType = Form.useWatch('discountType', form)

  const { data: coupons, isLoading } = useQuery({
    queryKey: ['coupons', includeInactive],
    queryFn: () => couponsApi.list(includeInactive),
  })

  const { mutate: createCoupon, isPending: isCreating } = useMutation({
    mutationFn: (data: CreateCouponRequest) => couponsApi.create(data),
    onSuccess: () => {
      message.success('Coupon created.')
      setModalOpen(false)
      queryClient.invalidateQueries({ queryKey: ['coupons'] })
    },
    onError: (err: { response?: { data?: { error?: string } } }) => {
      message.error(err.response?.data?.error ?? 'Failed to create coupon.')
    },
  })

  const { mutate: updateCoupon, isPending: isUpdating } = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Omit<CreateCouponRequest, 'code'> }) =>
      couponsApi.update(id, data),
    onSuccess: () => {
      message.success('Coupon updated.')
      setModalOpen(false)
      queryClient.invalidateQueries({ queryKey: ['coupons'] })
    },
    onError: (err: { response?: { data?: { error?: string } } }) => {
      message.error(err.response?.data?.error ?? 'Failed to update coupon.')
    },
  })

  const { mutate: setStatus } = useMutation({
    mutationFn: ({ id, isActive }: { id: string; isActive: boolean }) =>
      couponsApi.setStatus(id, isActive),
    onSuccess: (_, { isActive }) => {
      message.success(isActive ? 'Coupon activated.' : 'Coupon deactivated.')
      queryClient.invalidateQueries({ queryKey: ['coupons'] })
    },
    onError: () => message.error('Failed to change coupon status.'),
  })

  function openCreateModal() {
    setEditingCoupon(null)
    form.resetFields()
    form.setFieldsValue({ discountType: 'PERCENT' })
    setModalOpen(true)
  }

  function openEditModal(coupon: Coupon) {
    setEditingCoupon(coupon)
    form.setFieldsValue({
      code: coupon.code,
      discountType: coupon.discountType,
      value: coupon.value,
      minSubtotal: coupon.minSubtotal,
      validRange: [dayjs(coupon.validFrom), dayjs(coupon.validTo)],
      usageLimit: coupon.usageLimit,
    })
    setModalOpen(true)
  }

  function handleSubmit(values: CouponFormValues) {
    const [validFrom, validTo] = values.validRange
    const payload = {
      discountType: values.discountType,
      value: values.value,
      minSubtotal: values.minSubtotal ?? null,
      // Coupons are date-granularity ("valid through 30 Sep"); the server normalises
      // validTo to end-of-day IST, so sending start-of-day for both ends is sufficient —
      // no time picker needed here.
      validFrom: toApiDatetime(validFrom.startOf('day')),
      validTo: toApiDatetime(validTo.startOf('day')),
      usageLimit: values.usageLimit ?? null,
    }
    if (editingCoupon) {
      updateCoupon({ id: editingCoupon.id, data: payload })
    } else {
      createCoupon({ code: values.code, ...payload })
    }
  }

  const columns = [
    {
      title: 'Code',
      dataIndex: 'code',
      key: 'code',
      render: (code: string) => <Text strong>{code}</Text>,
    },
    {
      title: 'Type',
      dataIndex: 'discountType',
      key: 'discountType',
      render: (type: DiscountType) => <Tag color={type === 'PERCENT' ? 'purple' : 'blue'}>{type}</Tag>,
    },
    {
      title: 'Value',
      key: 'value',
      render: (_: unknown, row: Coupon) =>
        row.discountType === 'PERCENT' ? `${row.value}%` : formatCurrency(row.value),
    },
    {
      title: 'Min subtotal',
      key: 'minSubtotal',
      render: (_: unknown, row: Coupon) => row.minSubtotal !== null ? formatCurrency(row.minSubtotal) : '—',
    },
    {
      title: 'Validity',
      key: 'validity',
      render: (_: unknown, row: Coupon) => formatValidity(row.validFrom, row.validTo),
    },
    {
      title: 'Usage',
      key: 'usage',
      render: (_: unknown, row: Coupon) => `${row.timesUsed} / ${row.usageLimit ?? '∞'}`,
    },
    {
      title: 'Status',
      key: 'status',
      render: (_: unknown, row: Coupon) => (
        <Tag color={row.isActive ? 'green' : 'default'}>{row.isActive ? 'Active' : 'Inactive'}</Tag>
      ),
    },
    {
      title: '',
      key: 'actions',
      width: 140,
      render: (_: unknown, row: Coupon) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEditModal(row)} />
          <Popconfirm
            title={row.isActive ? `Deactivate ${row.code}?` : `Activate ${row.code}?`}
            onConfirm={() => setStatus({ id: row.id, isActive: !row.isActive })}
            okText={row.isActive ? 'Deactivate' : 'Activate'}
            okButtonProps={{ danger: row.isActive }}
          >
            <Button size="small" danger={row.isActive}>
              {row.isActive ? 'Deactivate' : 'Activate'}
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        label="Settings"
        title="Coupon"
        accent="Codes"
        action={
          <Space>
            <Space size={6}>
              <Text type="secondary" style={{ fontSize: 13 }}>Show inactive</Text>
              <Switch size="small" checked={includeInactive} onChange={setIncludeInactive} />
            </Space>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
              Create Coupon
            </Button>
          </Space>
        }
      />

      <Table
        columns={columns}
        dataSource={coupons ?? []}
        loading={isLoading}
        rowKey="id"
        size="small"
        pagination={false}
        scroll={{ x: 'max-content' }}
      />

      <Modal
        title={editingCoupon ? `Edit — ${editingCoupon.code}` : 'Create Coupon'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        okText={editingCoupon ? 'Save Changes' : 'Create'}
        confirmLoading={isCreating || isUpdating}
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit} style={{ marginTop: 16 }}>
          <Form.Item
            name="code"
            label="Code"
            rules={[
              { required: true, message: 'Code is required' },
              { pattern: /^[A-Za-z0-9_-]{3,32}$/, message: '3-32 letters, digits, hyphens or underscores' },
            ]}
          >
            <Input placeholder="e.g. SAVE20" disabled={!!editingCoupon} autoComplete="off" />
          </Form.Item>

          <Form.Item name="discountType" label="Discount Type" rules={[{ required: true }]}>
            <Select>
              <Select.Option value="PERCENT">Percent</Select.Option>
              <Select.Option value="FIXED">Fixed Amount</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="value"
            label={discountType === 'PERCENT' ? 'Value (%)' : 'Value (₹)'}
            rules={[{ required: true, message: 'Value is required' }]}
          >
            <InputNumber
              min={1}
              max={discountType === 'PERCENT' ? 100 : undefined}
              style={{ width: '100%' }}
            />
          </Form.Item>

          <Form.Item name="minSubtotal" label="Minimum rent subtotal (optional)">
            <InputNumber min={1} style={{ width: '100%' }} placeholder="No minimum" />
          </Form.Item>

          <Form.Item
            name="validRange"
            label="Valid dates"
            rules={[{ required: true, message: 'Validity range is required' }]}
          >
            <DatePicker.RangePicker format="DD MMM YYYY" style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="usageLimit" label="Usage limit (optional)">
            <InputNumber min={1} style={{ width: '100%' }} placeholder="Unlimited" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

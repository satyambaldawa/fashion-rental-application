import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery, useMutation } from '@tanstack/react-query'
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Col,
  Descriptions,
  Divider,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd'
import {
  ShopOutlined,
  ShoppingCartOutlined,
  PlusOutlined,
  MinusOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import type { Dayjs } from 'dayjs'
import DatePicker from 'antd/es/date-picker'
import { useCart } from '../../hooks/useCart'
import { toApiDatetime } from '../../utils/datetime'
import { itemsApi } from '../../api/items'
import { receiptsApi } from '../../api/receipts'
import CustomerSearch from '../../components/common/CustomerSearch'
import { customersApi } from '../../api/customers'
import type { CustomerSummary } from '../../types/customer'
import type { ItemSummary } from '../../types/inventory'
import type { CartItem, CheckoutRequest } from '../../types/receipt'
import { formatCurrency } from '../../utils/currency'

type Screen = 'home' | 'browse' | 'preview' | 'customer'

export default function CheckoutPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const { cart, createCart, addItem, removeItem, updateQuantity, clearCart } = useCart()

  const [screen, setScreen] = useState<Screen>(cart ? 'browse' : 'home')

  // Customer selection — declared before the useEffect that references setSelectedCustomer
  const [selectedCustomer, setSelectedCustomer] = useState<CustomerSummary | null>(null)

  // Auto-select customer returned from registration page
  useEffect(() => {
    const newCustomerId = searchParams.get('newCustomerId')
    if (newCustomerId) {
      customersApi.get(newCustomerId).then(customer => {
        setSelectedCustomer({
          id: customer.id,
          name: customer.name,
          phone: customer.phone,
          address: customer.address,
          customerType: customer.customerType,
          organizationName: customer.organizationName,
          activeRentalsCount: 0,
        })
        setScreen('customer')
      }).catch(() => {})
      setSearchParams({}, { replace: true })
    }
  }, [searchParams, setSearchParams])

  // Create cart modal
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [useNow, setUseNow] = useState(true)
  const [startPicker, setStartPicker] = useState<Dayjs | null>(null)
  const [rentalDays, setRentalDays] = useState(1)
  const [createError, setCreateError] = useState<string | null>(null)

  // Browse screen
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState<string | undefined>()

  // Preview / confirm
  const [conflictError, setConflictError] = useState<string | null>(null)

  // Items query — only runs when cart exists and we're browsing/previewing
  const { data: itemsPage, isLoading: itemsLoading } = useQuery({
    queryKey: ['items-for-cart', search, category, cart?.startDatetime, cart?.endDatetime],
    queryFn: () => itemsApi.list({
      search: search || undefined,
      category: category || undefined,
      size: 100,
      startDatetime: cart!.startDatetime,
      endDatetime: cart!.endDatetime,
    }),
    enabled: !!cart,
  })

  // Only show items that are actually available for the cart's dates
  const availableItems = (itemsPage?.content ?? []).filter(i => i.availableQuantity > 0)

  const createMutation = useMutation({
    mutationFn: (req: CheckoutRequest) => receiptsApi.create(req),
    onSuccess: (receipt) => {
      clearCart()
      navigate(`/receipts/${receipt.id}`)
    },
    onError: (err: unknown) => {
      const apiErr = err as { response?: { data?: { error?: string } } }
      setConflictError(apiErr?.response?.data?.error ?? 'Failed to create receipt. Please try again.')
    },
  })

  // --- Create cart helpers ---

  function handleOpenCreateModal() {
    setUseNow(true)
    setStartPicker(null)
    setRentalDays(1)
    setCreateError(null)
    setShowCreateModal(true)
  }

  function handleConfirmCreate() {
    const start = useNow ? dayjs() : startPicker
    if (!start) {
      setCreateError('Please select a start date and time.')
      return
    }
    const end = start.add(rentalDays, 'day')
    createCart(toApiDatetime(start), toApiDatetime(end), rentalDays)
    setShowCreateModal(false)
    setScreen('browse')
  }

  // --- Cart actions ---

  function handleAddToCart(item: ItemSummary) {
    const cartItem: CartItem = {
      itemId: item.id,
      itemName: item.name,
      thumbnailUrl: item.thumbnailUrl,
      rate: item.rate,
      deposit: item.deposit,
      quantity: 1,
      availableQuantity: item.availableQuantity,
    }
    addItem(cartItem)
  }

  function handleDeleteCart() {
    clearCart()
    setScreen('home')
  }

  // --- Preview ---

  function buildRequest(customerId: string): CheckoutRequest {
    return {
      customerId,
      startDatetime: cart!.startDatetime,
      endDatetime: cart!.endDatetime,
      items: cart!.items.map(i => ({ itemId: i.itemId, quantity: i.quantity })),
    }
  }

  function handleConfirmReceipt() {
    if (!selectedCustomer) return
    setConflictError(null)
    createMutation.mutate(buildRequest(selectedCustomer.id))
  }

  // --- Render helpers ---

  const cartCount = cart?.items.reduce((s, i) => s + i.quantity, 0) ?? 0
  const cartTotal = cart
    ? cart.items.reduce((s, i) => s + i.rate * cart.rentalDays * i.quantity + i.deposit * i.quantity, 0)
    : 0

  // ---- HOME ----
  if (screen === 'home') {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', paddingTop: 80, gap: 16 }}>
        <ShoppingCartOutlined style={{ fontSize: 64, color: '#bbb' }} />
        <Typography.Title level={4} style={{ margin: 0 }}>New Rental</Typography.Title>
        <Typography.Text type="secondary">Start by creating a cart with the rental period.</Typography.Text>
        <Button type="primary" size="large" icon={<PlusOutlined />} onClick={handleOpenCreateModal}>
          Create New Cart
        </Button>

        <CreateCartModal
          open={showCreateModal}
          useNow={useNow}
          startPicker={startPicker}
          rentalDays={rentalDays}
          error={createError}
          onUseNowChange={setUseNow}
          onStartPickerChange={setStartPicker}
          onDaysChange={setRentalDays}
          onConfirm={handleConfirmCreate}
          onCancel={() => setShowCreateModal(false)}
        />
      </div>
    )
  }

  // ---- BROWSE ----
  if (screen === 'browse') {
    return (
      <div style={{ paddingBottom: 100 }}>
        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
          <Typography.Title level={4} style={{ margin: 0 }}>Select Items</Typography.Title>
          <Space>
            <Tag color="blue">
              {dayjs(cart!.startDatetime).format('DD MMM HH:mm')} → {dayjs(cart!.endDatetime).format('DD MMM HH:mm')}
            </Tag>
            <Tag color="purple">{cart!.rentalDays} day{cart!.rentalDays !== 1 ? 's' : ''}</Tag>
          </Space>
        </div>

        {/* Filters */}
        <Space style={{ marginBottom: 16 }} wrap>
          <Input.Search
            placeholder="Search items..."
            style={{ width: 220 }}
            value={search}
            onChange={e => setSearch(e.target.value)}
            allowClear
            onClear={() => setSearch('')}
          />
          <Select
            allowClear
            placeholder="Category"
            style={{ width: 160 }}
            value={category}
            onChange={(v) => setCategory(v)}
            options={[
              { label: 'Costume', value: 'COSTUME' },
              { label: 'Accessories', value: 'ACCESSORIES' },
              { label: 'Pagdi', value: 'PAGDI' },
              { label: 'Dress', value: 'DRESS' },
              { label: 'Ornaments', value: 'ORNAMENTS' },
            ]}
          />
        </Space>

        {itemsLoading && <div style={{ textAlign: 'center', padding: 40 }}><Spin /></div>}

        {!itemsLoading && availableItems.length === 0 && (
          <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>
            No items available for the selected period.
          </div>
        )}

        <Row gutter={[16, 16]}>
          {availableItems.map(item => {
            const inCart = cart!.items.find(c => c.itemId === item.id)
            return (
              <Col key={item.id} xs={24} sm={12} lg={8}>
                <Card
                  cover={
                    item.thumbnailUrl ? (
                      <img
                        src={item.thumbnailUrl}
                        alt={item.name}
                        style={{ width: '100%', aspectRatio: '1/1', objectFit: 'cover' }}
                      />
                    ) : (
                      <div style={{ width: '100%', aspectRatio: '1/1', background: '#f0f0f0', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <ShopOutlined style={{ fontSize: 48, color: '#bbb' }} />
                      </div>
                    )
                  }
                  actions={[
                    inCart ? (
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 12 }} key="qty">
                        <Button
                          size="small"
                          icon={<MinusOutlined />}
                          onClick={() => inCart.quantity === 1 ? removeItem(item.id) : updateQuantity(item.id, inCart.quantity - 1)}
                        />
                        <Typography.Text strong style={{ minWidth: 20, textAlign: 'center' }}>
                          {inCart.quantity}
                        </Typography.Text>
                        <Button
                          size="small"
                          icon={<PlusOutlined />}
                          disabled={inCart.quantity >= item.availableQuantity}
                          onClick={() => updateQuantity(item.id, inCart.quantity + 1)}
                        />
                      </div>
                    ) : (
                      <Button
                        type="primary"
                        ghost
                        icon={<PlusOutlined />}
                        onClick={() => handleAddToCart(item)}
                        key="add"
                      >
                        Add to Cart
                      </Button>
                    ),
                  ]}
                >
                  <Card.Meta
                    title={item.name}
                    description={
                      <Space direction="vertical" size={4} style={{ width: '100%' }}>
                        <Space>
                          <Tag color="blue">{item.category}</Tag>
                          {item.size && <Tag>{item.size}</Tag>}
                        </Space>
                        <div>{formatCurrency(item.rate)}/day</div>
                        <div style={{ color: '#666' }}>Deposit: {formatCurrency(item.deposit)}</div>
                        <Tag color="success">{item.availableQuantity} available</Tag>
                        {inCart && <Tag color="orange">In cart ×{inCart.quantity}</Tag>}
                      </Space>
                    }
                  />
                </Card>
              </Col>
            )
          })}
        </Row>

        {/* Sticky bottom bar */}
        <div style={{
          position: 'fixed', bottom: 0, left: 220, right: 0,
          background: '#fff', borderTop: '1px solid #f0f0f0',
          padding: '12px 24px',
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          zIndex: 100,
        }}>
          <Space>
            <ShoppingCartOutlined style={{ fontSize: 20 }} />
            <Typography.Text strong>
              {cartCount} item{cartCount !== 1 ? 's' : ''} · {formatCurrency(cartTotal)}
            </Typography.Text>
          </Space>
          <Space>
            <Button danger onClick={handleDeleteCart}>Delete Cart</Button>
            <Button
              type="primary"
              disabled={cartCount === 0}
              onClick={() => setScreen('preview')}
            >
              Checkout
            </Button>
          </Space>
        </div>
      </div>
    )
  }

  // ---- PREVIEW ----
  if (screen === 'preview') {
    const previewColumns = [
      { title: 'Item', dataIndex: 'itemName', key: 'name' },
      {
        title: 'Qty',
        dataIndex: 'quantity',
        key: 'qty',
        render: (qty: number, row: CartItem) => (
          <InputNumber
            min={1}
            max={row.availableQuantity}
            value={qty}
            onChange={(v) => { if (v && v >= 1) updateQuantity(row.itemId, v) }}
            size="small"
          />
        ),
      },
      { title: 'Rate/day', key: 'rate', render: (_: unknown, r: CartItem) => formatCurrency(r.rate) },
      { title: 'Deposit', key: 'deposit', render: (_: unknown, r: CartItem) => formatCurrency(r.deposit) },
      {
        title: 'Line Rent',
        key: 'lineRent',
        render: (_: unknown, r: CartItem) => formatCurrency(r.rate * cart!.rentalDays * r.quantity),
      },
      {
        title: 'Line Deposit',
        key: 'lineDeposit',
        render: (_: unknown, r: CartItem) => formatCurrency(r.deposit * r.quantity),
      },
    ]

    const totalRent = cart!.items.reduce((s, i) => s + i.rate * cart!.rentalDays * i.quantity, 0)
    const totalDeposit = cart!.items.reduce((s, i) => s + i.deposit * i.quantity, 0)
    const grandTotal = totalRent + totalDeposit

    return (
      <div style={{ maxWidth: 860 }}>
        <Typography.Title level={4}>Order Preview</Typography.Title>

        <Descriptions size="small" style={{ marginBottom: 16 }}>
          <Descriptions.Item label="Start">{dayjs(cart!.startDatetime).format('DD MMM YYYY HH:mm')}</Descriptions.Item>
          <Descriptions.Item label="End">{dayjs(cart!.endDatetime).format('DD MMM YYYY HH:mm')}</Descriptions.Item>
          <Descriptions.Item label="Duration">{cart!.rentalDays} day{cart!.rentalDays !== 1 ? 's' : ''}</Descriptions.Item>
        </Descriptions>

        <Table
          dataSource={cart!.items}
          columns={previewColumns}
          rowKey="itemId"
          pagination={false}
          size="small"
          style={{ marginBottom: 24 }}
        />

        <Card size="small" style={{ maxWidth: 360, marginBottom: 24 }}>
          <Descriptions column={1} size="small">
            <Descriptions.Item label="Total Rent">{formatCurrency(totalRent)}</Descriptions.Item>
            <Descriptions.Item label="Total Deposit">{formatCurrency(totalDeposit)}</Descriptions.Item>
            <Descriptions.Item label={<strong>Grand Total</strong>}>
              <strong>{formatCurrency(grandTotal)}</strong>
            </Descriptions.Item>
          </Descriptions>
        </Card>

        <Space>
          <Button onClick={() => setScreen('browse')}>Back to Items</Button>
          <Button type="primary" onClick={() => setScreen('customer')}>
            Confirm & Proceed
          </Button>
        </Space>
      </div>
    )
  }

  // ---- CUSTOMER SELECTION ----
  if (screen === 'customer') {
    const totalRent = cart!.items.reduce((s, i) => s + i.rate * cart!.rentalDays * i.quantity, 0)
    const totalDeposit = cart!.items.reduce((s, i) => s + i.deposit * i.quantity, 0)

    return (
      <div style={{ maxWidth: 560 }}>
        <Typography.Title level={4}>Select Customer</Typography.Title>

        <Typography.Paragraph type="secondary">
          Search by phone number or name. If the customer is new, register them first.
        </Typography.Paragraph>

        <CustomerSearch onSelect={setSelectedCustomer} placeholder="Search by phone or name..." />

        {selectedCustomer && (
          <Card size="small" style={{ marginTop: 16, marginBottom: 24 }}>
            <Descriptions column={1} size="small">
              <Descriptions.Item label="Name">{selectedCustomer.name}</Descriptions.Item>
              <Descriptions.Item label="Phone">{selectedCustomer.phone}</Descriptions.Item>
              {selectedCustomer.organizationName && (
                <Descriptions.Item label="Organisation">{selectedCustomer.organizationName}</Descriptions.Item>
              )}
            </Descriptions>
          </Card>
        )}

        <Divider />

        <Descriptions column={1} size="small" style={{ marginBottom: 24 }}>
          <Descriptions.Item label="Items">{cartCount}</Descriptions.Item>
          <Descriptions.Item label="Total Rent">{formatCurrency(totalRent)}</Descriptions.Item>
          <Descriptions.Item label="Total Deposit">{formatCurrency(totalDeposit)}</Descriptions.Item>
          <Descriptions.Item label={<strong>Grand Total</strong>}>
            <strong>{formatCurrency(totalRent + totalDeposit)}</strong>
          </Descriptions.Item>
        </Descriptions>

        {conflictError && (
          <Alert type="error" message={conflictError} style={{ marginBottom: 16 }} />
        )}

        <Space>
          <Button onClick={() => setScreen('preview')}>Back</Button>
          <Button onClick={() => navigate('/customers/register?returnTo=checkout')}>New Customer</Button>
          <Button
            type="primary"
            disabled={!selectedCustomer}
            loading={createMutation.isPending}
            onClick={handleConfirmReceipt}
          >
            Create Receipt
          </Button>
        </Space>
      </div>
    )
  }

  return null
}

// ---- Create Cart Modal ----

interface CreateCartModalProps {
  open: boolean
  useNow: boolean
  startPicker: Dayjs | null
  rentalDays: number
  error: string | null
  onUseNowChange: (v: boolean) => void
  onStartPickerChange: (v: Dayjs | null) => void
  onDaysChange: (v: number) => void
  onConfirm: () => void
  onCancel: () => void
}

function CreateCartModal({
  open, useNow, startPicker, rentalDays, error,
  onUseNowChange, onStartPickerChange, onDaysChange, onConfirm, onCancel,
}: CreateCartModalProps) {
  const previewStart = useNow ? dayjs() : startPicker
  const previewEnd = previewStart ? previewStart.add(rentalDays, 'day') : null

  return (
    <Modal
      open={open}
      title="Create New Cart"
      onOk={onConfirm}
      onCancel={onCancel}
      okText="Start Browsing"
    >
      <Space direction="vertical" size="large" style={{ width: '100%', paddingTop: 8 }}>
        <div>
          <Typography.Text strong>Start Time</Typography.Text>
          <div style={{ marginTop: 8 }}>
            <Checkbox
              checked={useNow}
              onChange={e => onUseNowChange(e.target.checked)}
              style={{ marginBottom: 8 }}
            >
              Use current time
            </Checkbox>
            {!useNow && (
              <DatePicker
                showTime
                format="DD MMM YYYY HH:mm"
                value={startPicker}
                onChange={onStartPickerChange}
                style={{ width: '100%' }}
              />
            )}
          </div>
        </div>

        <div>
          <Typography.Text strong>Number of Days</Typography.Text>
          <div style={{ marginTop: 8 }}>
            <InputNumber
              min={1}
              max={30}
              value={rentalDays}
              onChange={v => onDaysChange(v ?? 1)}
              addonAfter="days"
              style={{ width: 160 }}
            />
          </div>
        </div>

        {previewEnd && (
          <Alert
            type="info"
            message={
              `Return by: ${previewEnd.format('DD MMM YYYY HH:mm')} · ${rentalDays} day${rentalDays !== 1 ? 's' : ''}`
            }
          />
        )}

        {error && <Alert type="error" message={error} />}
      </Space>
    </Modal>
  )
}
